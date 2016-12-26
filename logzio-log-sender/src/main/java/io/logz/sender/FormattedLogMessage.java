package io.logz.sender;

public class FormattedLogMessage {

    private byte[] message;

    public FormattedLogMessage(byte[] message) {
        this.message = message;
    }

    public byte[] getMessage() {
        return message;
    }

    public int getSize() {
        return message.length;
    }
}
