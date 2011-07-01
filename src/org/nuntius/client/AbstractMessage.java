package org.nuntius.client;

import java.text.ParseException;
import java.util.*;

import org.nuntius.data.MessageID;
import org.nuntius.provider.MyMessages.Messages;

import android.content.AsyncQueryHandler;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcelable;
import android.util.Log;


/**
 * An abstract message.
 * FIXME it should be a {@link Parcelable}
 * @author Daniele Ricci
 * @version 1.0
 */
public abstract class AbstractMessage<T> {
    private static final String TAG = AbstractMessage.class.getSimpleName();

    private static final String[] MESSAGE_LIST_PROJECTION = {
        Messages._ID,
        Messages.MESSAGE_ID,
        Messages.PEER,
        Messages.DIRECTION,
        Messages.TIMESTAMP,
        Messages.MIME,
        Messages.CONTENT,
        Messages.STATUS,
        Messages.FETCH_URL,
        Messages.FETCHED,
        Messages.LOCAL_URI
    };

    public static final String MSG_ID = "org.nuntius.message.id";
    public static final String MSG_SENDER = "org.nuntius.message.sender";
    public static final String MSG_MIME = "org.nuntius.message.mime";
    public static final String MSG_CONTENT = "org.nuntius.message.content";
    public static final String MSG_RECIPIENTS = "org.nuntius.message.recipients";
    public static final String MSG_GROUP = "org.nuntius.message.group";
    public static final String MSG_TIMESTAMP = "org.nuntius.message.timestamp";

    protected Context mContext;
    protected String id;
    protected String sender;
    protected String mime;
    protected T content;
    protected long timestamp;
    protected int status;
    protected boolean fetched;
    protected MessageID messageId;

    /**
     * Recipients (outgoing) - will contain one element for incoming
     */
    protected List<String> recipients;

    /**
     * Recipients (incoming) - will be null for outgoing
     */
    protected List<String> group;

    /** Remote fetch URL (if any). */
    protected String fetchUrl;

    /** Local file {@link Uri}. */
    protected Uri localUri;

    public AbstractMessage(Context context, String id, String sender, String mime, T content, List<String> group) {
        this(context, id, sender, mime, content);
        this.group = group;
    }

    public AbstractMessage(Context context, String id, String sender, String mime, T content) {
        this.mContext = context;

        if (id != null) setId(id);
        this.sender = sender;
        this.mime = mime;
        this.content = content;
        this.recipients = new ArrayList<String>();
        // will be updated if necessary
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
        try {
            this.messageId = MessageID.parse(id);
        }
        catch (ParseException e) {
            Log.e(TAG, "invalid server message id - " + id);
        }
    }

    public String getSender() {
        return sender;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void addRecipient(String userId) {
        recipients.add(userId);
    }

    public List<String> getGroup() {
        return group;
    }

    public String getMime() {
        return mime;
    }

    public T getContent() {
        return content;
    }

    public void setContent(T content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Date getServerTimestamp() {
        return (messageId != null) ? messageId.getDate() : null;
    }

    public int getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": id=" + id;
    }

    public int getDirection() {
        return (sender != null) ?
                Messages.DIRECTION_IN : Messages.DIRECTION_OUT;
    }

    /**
     * Returns a rapid text representation of the message.
     * The returned value is useful for notification tickers.
     * @return the text that represent this message
     */
    public abstract String getTextContent();

    /** Sets a URL for fetching big contents. */
    public void setFetchUrl(String url) {
        fetchUrl = url;
    }

    public String getFetchUrl() {
        return fetchUrl;
    }

    public void setFetched(boolean fetched) {
        this.fetched = fetched;
    }

    public boolean isFetched() {
        return this.fetched;
    }

    /** Sets a pointer to the local resource. */
    public void setLocalUri(Uri uri) {
        localUri = uri;
    }

    public Uri getLocalUri() {
        return localUri;
    }

    protected void populateFromCursor(Cursor c) {
        setId(c.getString(c.getColumnIndex(Messages.MESSAGE_ID)));
        mime = c.getString(c.getColumnIndex(Messages.MIME));
        timestamp = c.getLong(c.getColumnIndex(Messages.TIMESTAMP));
        status = c.getInt(c.getColumnIndex(Messages.STATUS));
        recipients = new ArrayList<String>();
        fetchUrl = c.getString(c.getColumnIndex(Messages.FETCH_URL));
        fetched = (c.getShort(c.getColumnIndex(Messages.FETCHED)) != 0);

        String peer = c.getString(c.getColumnIndex(Messages.PEER));
        int direction = c.getInt(c.getColumnIndex(Messages.DIRECTION));
        if (direction == Messages.DIRECTION_OUT) {
            // we are the origin
            sender = null;
            recipients.add(peer);
        }
        else {
            sender = peer;
            // we are the origin - no recipient
        }

        // TODO groups??
    }

    public static AbstractMessage<?> fromCursor(Context context, Cursor cursor) {
        String mime = cursor.getString(cursor.getColumnIndex(Messages.MIME));
        if (PlainTextMessage.supportsMimeType(mime)) {
            PlainTextMessage msg = new PlainTextMessage(context);
            msg.populateFromCursor(cursor);
            return msg;
        }

        else if (ImageMessage.supportsMimeType(mime)) {
            ImageMessage msg = new ImageMessage(context);
            msg.populateFromCursor(cursor);
            return msg;
        }

        return null;
    }

    public static void startQuery(AsyncQueryHandler handler, int token, long threadId) {
        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, null, Messages.CONTENT_URI,
                MESSAGE_LIST_PROJECTION, "thread_id = ?", new String[] { String.valueOf(threadId) },
                    Messages.DEFAULT_SORT_ORDER);
    }

    public static void startQuery(AsyncQueryHandler handler, int token, String peer) {
        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, null, Messages.CONTENT_URI,
                MESSAGE_LIST_PROJECTION, "peer = ?", new String[] { peer },
                    Messages.DEFAULT_SORT_ORDER);
    }

}
