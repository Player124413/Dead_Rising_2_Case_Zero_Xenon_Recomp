package com.casezero.launcher;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class SafeFilesTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private byte[] zip(String... names) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String name : names) { zip.putNextEntry(new ZipEntry(name)); if (!name.endsWith("/")) zip.write(new byte[16]); zip.closeEntry(); }
        }
        return out.toByteArray();
    }
    private SafeFiles.Budget budget(long bytes, int files) { return new SafeFiles.Budget(bytes, files, n -> {}); }
    @Test public void rejectsTraversalAndAbsoluteNames() {
        for (String name : new String[]{"../x", "/x", "C:/x", "a\\b", "a/../b", "a//b", "./b", "a\0b", ""})
            assertThrows(name, IOException.class, () -> SafeFiles.relative(name));
    }
    @Test public void streamsNestedArchive() throws Exception {
        File root = temp.newFolder();
        SafeFiles.unzip(new ByteArrayInputStream(zip("nested/", "nested/file")), root, budget(64, 5));
        assertEquals(16, new File(root, "nested/file").length());
    }
    @Test public void rejectsCaseInsensitiveCollisions() throws Exception {
        assertThrows(IOException.class, () -> SafeFiles.unzip(new ByteArrayInputStream(zip("A", "a")), temp.newFolder(), budget(64, 5)));
    }
    @Test public void rejectsZipSlip() throws Exception {
        File root = temp.newFolder();
        assertThrows(IOException.class, () -> SafeFiles.unzip(new ByteArrayInputStream(zip("../escape")), root, budget(64, 5)));
        assertFalse(new File(root.getParentFile(), "escape").exists());
    }
    @Test public void boundsExpandedBytesAndEntryCount() throws Exception {
        assertThrows(IOException.class, () -> SafeFiles.unzip(new ByteArrayInputStream(zip("large")), temp.newFolder(), budget(4, 5)));
        assertThrows(IOException.class, () -> SafeFiles.unzip(new ByteArrayInputStream(zip("a/", "b/")), temp.newFolder(), budget(64, 1)));
    }
    @Test public void cancellationLeavesPriorDataAlone() throws Exception {
        File current = temp.newFolder(); Files.write(new File(current,"saved").toPath(), new byte[]{42});
        SafeFiles.Budget cancel = new SafeFiles.Budget(100, 10, n -> { throw new InterruptedIOException("Cancelled"); });
        assertThrows(InterruptedIOException.class, () -> SafeFiles.unzip(new ByteArrayInputStream(zip("new")), temp.newFolder(), cancel));
        assertArrayEquals(new byte[]{42}, Files.readAllBytes(new File(current,"saved").toPath()));
    }
    @Test public void emptyOrCorruptZipFails() throws Exception {
        assertThrows(IOException.class, () -> SafeFiles.unzip(new ByteArrayInputStream(new byte[]{1,2}), temp.newFolder(), budget(64,5)));
        byte[] truncated = zip("a");
        assertThrows(IOException.class, () -> SafeFiles.unzip(new ByteArrayInputStream(java.util.Arrays.copyOf(truncated, 35)), temp.newFolder(), budget(64,5)));
    }
    @Test public void replacementAndInterruptedCommitRecover() throws Exception {
        File parent = temp.newFolder(), current = new File(parent,"game"), incoming = new File(parent,"incoming");
        SafeFiles.mkdir(current); SafeFiles.mkdir(incoming);
        Files.write(new File(current,"old").toPath(), new byte[]{1}); Files.write(new File(incoming,"new").toPath(), new byte[]{2});
        SafeFiles.replaceDirectory(incoming, current);
        assertTrue(new File(current,"new").isFile()); assertTrue(new File(parent,"game.previous/old").isFile());
        SafeFiles.deleteTree(current); SafeFiles.recoverDirectory(current);
        assertTrue(new File(current,"old").isFile());
    }
    @Test public void symlinkCannotEscapeRoot() throws Exception {
        File parent = temp.newFolder(), root = new File(parent,"root"), outside = new File(parent,"outside");
        SafeFiles.mkdir(root); SafeFiles.mkdir(outside);
        Files.createSymbolicLink(new File(root,"link").toPath(), outside.toPath());
        assertThrows(IOException.class, () -> SafeFiles.resolve(root,"link/file"));
        SafeFiles.deleteTree(root); assertTrue(outside.exists());
    }
    private File xex(int title, int offset) throws Exception {
        File f = temp.newFile(); byte[] bytes = new byte[64];
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        b.putInt(0,0x58455832); b.putInt(20,1); b.putInt(24,0x00040006); b.putInt(28,offset); b.putInt(44,title);
        Files.write(f.toPath(),bytes); return f;
    }
    @Test public void validatesGameIdentityAndBounds() throws Exception {
        GameFiles.validateXex(xex(GameFiles.TITLE_ID,32));
        assertThrows(IOException.class, () -> GameFiles.validateXex(xex(123,32)));
        assertThrows(IOException.class, () -> GameFiles.validateXex(xex(GameFiles.TITLE_ID,Integer.MAX_VALUE)));
    }
    @Test public void atomicSettingsAreComplete() throws Exception {
        File f = temp.newFile(); SafeFiles.atomicText(f,"version=1\n"); SafeFiles.atomicText(f,"version=2\n");
        assertEquals("version=2\n",SafeFiles.readText(f,64)); assertFalse(new File(f + ".tmp").exists());
    }
    @Test public void boundedReadsRejectOversizedMetadata() {
        assertThrows(IOException.class, () -> SafeFiles.readLimited(new ByteArrayInputStream(new byte[65]),64));
    }
}
