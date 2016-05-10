package io.logz.src;

/**
 * Created by roiravhon on 5/10/16.
 */
public class LogMessage {

    private byte[] message;

    public LogMessage(String message) {

        this.message = message.getBytes();
    }

    public byte[] getMessage() {
        return message;
    }

    public void setMessage(byte[] message) {
        this.message = message;
    }

    public int getSize() {

        return message.length;
    }
}
