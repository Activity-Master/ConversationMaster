open module conversations.master.tests {
    requires com.guicedee.activitymaster.conversations;
    requires com.guicedee.guicedinjection;
    requires org.junit.jupiter.api;
    requires org.testcontainers;
    exports com.guicedee.activitymaster.conversations.test;
    provides com.guicedee.client.services.lifecycle.IGuiceModule
        with com.guicedee.activitymaster.conversations.test.PostgreSQLTestDBModule;
}
