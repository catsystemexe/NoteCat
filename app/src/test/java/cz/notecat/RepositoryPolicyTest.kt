package cz.notecat

import cz.notecat.data.Note
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RepositoryPolicyTest { @Test fun idempotencyKeysDifferPerNote() { assertNotEquals(Note().idempotencyKey, Note().idempotencyKey) } }
