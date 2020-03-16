/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.ui;

import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;

import android.os.IBinder;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.kontalk.BuildConfig;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.provider.UsersProvider;
import org.kontalk.service.registration.RegistrationService;
import org.kontalk.service.registration.event.AccountCreatedEvent;
import org.kontalk.service.registration.event.ChallengeError;
import org.kontalk.service.registration.event.ChallengeRequest;
import org.kontalk.util.InternalTrustStore;
import org.kontalk.util.SystemUtils;


/** Manual validation code input. */
public class CodeValidation extends AccountAuthenticatorActionBarActivity {
    private static final String TAG = NumberValidation.TAG;

    private EditText mCode;
    private Button mButton;
    private Button mFallbackButton;
    private Button mCallButton;
    private ProgressBar mProgress;

    Map<String, String> mTrustedKeys;

    private EventBus mServiceBus = RegistrationService.bus();
    /** Just a dummy service connection to keep the service alive. */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    private static final class RetainData {
        Map<String, String> trustedKeys;

        RetainData() {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.code_validation_screen);
        setupToolbar(true, false);

        mCode = findViewById(R.id.validation_code);
        mButton = findViewById(R.id.send_button);
        mFallbackButton = findViewById(R.id.fallback_button);
        mCallButton = findViewById(R.id.code_validation_call);
        mProgress = findViewById(R.id.progressbar);

        // configuration change??
        RetainData data = (RetainData) getLastCustomNonConfigurationInstance();
        if (data != null) {
            // TODO when to startProgress(); ?
            mTrustedKeys = data.trustedKeys;
        }

        Intent intent = getIntent();
        RegistrationService.CurrentState cstate = RegistrationService.currentState();

        String phone = cstate.phoneNumber;

        int requestCode = getIntent().getIntExtra("requestCode", -1);
        if (requestCode == NumberValidation.REQUEST_VALIDATION_CODE ||
                getIntent().getStringExtra("sender") == null) {
            findViewById(R.id.code_validation_phone)
                .setVisibility(View.GONE);
            findViewById(R.id.code_validation_intro2)
                .setVisibility(View.GONE);
            ((TextView) findViewById(R.id.code_validation_intro))
                .setText(R.string.code_validation_intro_manual);
        }
        else {
            String challenge = cstate.challenge;
            String sender = intent.getStringExtra("sender");
            boolean canFallback = intent.getBooleanExtra("canFallback", false);

            final TextView phoneText = findViewById(R.id.code_validation_phone);
            String formattedPhone;
            try {
                PhoneNumberUtil util = PhoneNumberUtil.getInstance();
                Phonenumber.PhoneNumber phoneNumber = util.parse(phone, null);
                formattedPhone = util.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
            }
            catch (NumberParseException e) {
                formattedPhone = phone;
            }

            CharSequence textId1, textId2;
            if (RegistrationService.isMissedCall(sender) || RegistrationService.CHALLENGE_MISSED_CALL.equals(challenge)) {
                // reverse missed call
                textId1 = getText(R.string.code_validation_intro_missed_call);
                textId2 = getString(R.string.code_validation_intro2_missed_call,
                    RegistrationService.getChallengeLength(sender));
                if (canFallback) {
                    mFallbackButton.setText(R.string.button_validation_fallback);
                    mFallbackButton.setVisibility(View.VISIBLE);
                }
                // show sender label and hide call button
                phoneText.setText(formattedPhone);
                phoneText.setVisibility(View.VISIBLE);
                mCallButton.setVisibility(View.GONE);
                mCode.setVisibility(View.VISIBLE);
            }
            else if (RegistrationService.CHALLENGE_CALLER_ID.equals(challenge)) {
                // user-initiated missed call
                textId1 = getText(R.string.code_validation_intro_callerid);
                textId2 = getText(R.string.code_validation_intro2_callerid);
                if (canFallback) {
                    mFallbackButton.setText(R.string.button_validation_fallback_callerid);
                    mFallbackButton.setVisibility(View.VISIBLE);
                }
                // show call button and hide sender label
                mCallButton.setText(sender);
                mCallButton.setVisibility(View.VISIBLE);
                phoneText.setVisibility(View.GONE);
                mCode.setVisibility(View.GONE);
                // the incoming foreign number notice doesn't apply in this case
                findViewById(R.id.code_validation_intro3).setVisibility(View.GONE);
            }
            else {
                // PIN code
                textId1 = getText(R.string.code_validation_intro);
                textId2 = getText(R.string.code_validation_intro2);
                if (canFallback) {
                    mFallbackButton.setText(R.string.button_validation_fallback);
                    mFallbackButton.setVisibility(View.VISIBLE);
                }
                // show sender label and hide call button
                phoneText.setText(formattedPhone);
                phoneText.setVisibility(View.VISIBLE);
                mCallButton.setVisibility(View.GONE);
                mCode.setVisibility(View.VISIBLE);
            }

            if (mCallButton.getVisibility() == View.VISIBLE) {
                mCallButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        SystemUtils.dial(CodeValidation.this, mCallButton.getText());
                    }
                });
            }

            ((TextView) findViewById(R.id.code_validation_intro)).setText(textId1);
            ((TextView) findViewById(R.id.code_validation_intro2)).setText(textId2);
        }

        mTrustedKeys = (HashMap) intent.getSerializableExtra("trustedKeys");

        // brand information
        final String brandImage = intent.getStringExtra("brandImage");
        final String brandLink = intent.getStringExtra("brandLink");
        if (brandImage != null) {
            findViewById(R.id.brand_poweredby).setVisibility(View.VISIBLE);

            // we should use our builtin keystore
            try {
                InternalTrustStore.initUrlConnections(this);
            }
            catch (Exception e) {
                Log.w(TAG, "unable to initialize internal trust store", e);
            }

            final View brandParent = findViewById(R.id.brand_parent);
            brandParent.setVisibility(View.VISIBLE);
            final ProgressBar brandProgress = findViewById(R.id.brand_loading);
            brandProgress.setVisibility(View.VISIBLE);
            final ImageView brandView = findViewById(R.id.brand);
            brandView.setVisibility(View.VISIBLE);

            Glide.with(this)
                .load(brandImage)
                .listener(new RequestListener<String, GlideDrawable>() {

                    @Override
                    public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
                        brandParent.setVisibility(View.GONE);
                        brandView.setVisibility(View.GONE);
                        brandProgress.setVisibility(View.GONE);
                        if (brandLink != null) {
                            TextView brandTextView = findViewById(R.id.brand_text);
                            brandTextView.setText(brandLink);
                            brandTextView.setVisibility(View.VISIBLE);
                        }
                        return true;
                    }

                    @Override
                    public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                        brandProgress.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(brandView);

            if (brandLink != null) {
                brandView.setContentDescription(brandLink);
                brandView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SystemUtils.openURL(CodeValidation.this, brandLink);
                    }
                });
            }
        }
    }

    /** Not used. */
    @Override
    protected boolean isNormalUpNavigation() {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        new MaterialDialog.Builder(this)
            .title(R.string.title_confirm_cancel_registration)
            .content(R.string.confirm_cancel_registration)
            .positiveText(android.R.string.ok)
            .positiveColorRes(R.color.button_danger)
            .negativeText(android.R.string.cancel)
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog materialDialog, @NonNull DialogAction dialogAction) {
                    // we are going back voluntarily
                    RegistrationService.clearSavedState();
                    CodeValidation.super.onBackPressed();
                }
            })
            .show();
    }

    /** No search here. */
    @Override
    public boolean onSearchRequested() {
        return false;
    }

    /** Returning the validator thread. */
    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        RetainData data = new RetainData();
        data.trustedKeys = mTrustedKeys;
        return data;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // start a users resync in the meantime
        new UsersResyncTask().execute(getApplicationContext());
        // be sure to bind to the registration service so it's not killed by the OS
        bindService(new Intent(this, RegistrationService.class), mServiceConnection, BIND_IMPORTANT);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isFinishing())
            abort(true);

        try {
            unbindService(mServiceConnection);
        }
        catch (IllegalArgumentException ignored) {
        }
    }

    void keepScreenOn(boolean active) {
        if (active)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void error(int message) {
        new MaterialDialog.Builder(this)
            .content(message)
            .positiveText(android.R.string.ok)
            .show();
    }

    public void doFallback(View view) {
        new MaterialDialog.Builder(this)
            .title(R.string.title_fallback)
            .content(R.string.msg_fallback)
            .positiveText(android.R.string.ok)
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    setResult(NumberValidation.RESULT_FALLBACK);
                    finish();
                }
            })
            .negativeText(android.R.string.cancel)
            .show();
    }

    public void validateCode(View view) {
        String code = null;
        String challenge = getIntent().getStringExtra("challenge");
        if (!RegistrationService.CHALLENGE_CALLER_ID.equals(challenge)) {
            code = mCode.getText().toString().trim();
            if (code.length() == 0) {
                error(R.string.msg_invalid_code);
                return;
            }
        }

        startProgress();

        mServiceBus.register(this);
        mServiceBus.post(new ChallengeRequest(code));
    }

    private void enableControls(boolean enabled) {
        mButton.setEnabled(enabled);
        mFallbackButton.setEnabled(enabled);
        mCallButton.setEnabled(enabled);
        mCode.setEnabled(enabled);
    }

    private void startProgress() {
        if (!BuildConfig.TESTING.get()) {
            mProgress.setVisibility(View.VISIBLE);
        }
        enableControls(false);
        keepScreenOn(true);
    }

    void abort(boolean ending) {
        if (!ending) {
            mProgress.setVisibility(View.INVISIBLE);
            enableControls(true);
        }
        else {
            // ending - clear registration progress
            RegistrationService.clearSavedState();
        }
        keepScreenOn(false);
        mServiceBus.unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onChallengeError(ChallengeError error) {
        Log.e(TAG, "validation error.", error.exception);
        keepScreenOn(false);
        int msgId;
        if (error.exception instanceof SocketException) {
            msgId = R.string.err_validation_network_error;
        }
        else {
            String challenge = RegistrationService.currentState().challenge;
            if (RegistrationService.CHALLENGE_CALLER_ID.equals(challenge)) {
                // we are verifying through user-initiated missed call
                // notify the user that the verification didn't succeed
                msgId = R.string.err_authentication_failed_callerid;
            }
            else {
                // we are verifying through PIN-based challenge
                // notify the user that the challenge code wasn't accepted
                msgId = R.string.err_authentication_failed;
            }
        }
        Toast.makeText(this, msgId, Toast.LENGTH_LONG).show();
        abort(false);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAccountCreated(AccountCreatedEvent event) {
        RegistrationService.stop(this);

        abort(true);
        Intent i = new Intent();
        i.putExtra(NumberValidation.PARAM_ACCOUNT_NAME, event.account.name);
        setResult(RESULT_OK, i);
        finish();
    }

    private static final class UsersResyncTask extends AsyncTask<Context, Void, Void> {

        @Override
        protected Void doInBackground(Context... contexts) {
            UsersProvider.resync(contexts[0]);
            return null;
        }
    }
}
