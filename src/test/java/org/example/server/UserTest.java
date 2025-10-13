// java
package org.example.server;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void testConstructorAndGetters() {
        User u = new User("alice", "hash123");
        assertEquals("alice", u.getUsername());
        assertEquals("hash123", u.getPasswordHash());
        assertNull(u.getLastLogin());
        assertFalse(u.isOnline());
    }

    @Test
    void testSetters() {
        User u = new User("bob", "h");
        u.setUsername("bob2");
        u.setPasswordHash("h2");
        LocalDateTime now = LocalDateTime.now();
        u.setLastLogin(now);
        u.setOnline(true);

        assertEquals("bob2", u.getUsername());
        assertEquals("h2", u.getPasswordHash());
        assertEquals(now, u.getLastLogin());
        assertTrue(u.isOnline());
    }

    @Test
    void testSerializable() throws Exception {
        User u = new User("serial", "s");
        u.setOnline(true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(u);
        }

        byte[] data = baos.toByteArray();
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            Object obj = ois.readObject();
            assertTrue(obj instanceof User);
            User deserialized = (User) obj;
            assertEquals("serial", deserialized.getUsername());
            assertEquals("s", deserialized.getPasswordHash());
            assertTrue(deserialized.isOnline());
        }
    }
}