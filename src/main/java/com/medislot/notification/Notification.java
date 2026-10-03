package com.medislot.notification;

public record Notification(Channel channel, String recipient, String subject, String body) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Channel channel;
        private String recipient;
        private String subject = "";
        private String body = "";

        public Builder channel(Channel channel) { this.channel = channel; return this; }
        public Builder recipient(String recipient) { this.recipient = recipient; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder body(String body) { this.body = body; return this; }

        public Notification build() {
            if (channel == null || recipient == null || recipient.isBlank()) {
                throw new IllegalStateException("channel and recipient are required");
            }
            return new Notification(channel, recipient, subject, body);
        }
    }
}
