package org.aspends.nglyphs.util;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.aspends.nglyphs.R;

public class CustomRingtoneManager {
    private static final String RINGTONE_DIR = "custom_ringtones";

    public static File importFile(Context context, Uri uri, String fileName) {
        File dir = new File(context.getFilesDir(), RINGTONE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File destination = new File(dir, fileName);
        try (InputStream is = context.getContentResolver().openInputStream(uri);
                OutputStream os = new FileOutputStream(destination)) {
            if (is == null)
                return null;
            byte[] buffer = new byte[4096];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            return destination;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Copies the picked OGG file to internal storage.
     * @deprecated Use importFile instead.
     */
    @Deprecated
    public static File importOgg(Context context, Uri uri, String fileName) {
        return importFile(context, uri, fileName);
    }

    /**
     * Lists all imported custom files (ogg or csv).
     */
    public static List<File> getImportedFiles(Context context) {
        List<File> filesList = new ArrayList<>();
        File dir = new File(context.getFilesDir(), RINGTONE_DIR);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().endsWith(".ogg") || f.getName().endsWith(".csv")) {
                        filesList.add(f);
                    }
                }
            }
        }
        return filesList;
    }

    public static List<File> getImportedRingtones(Context context) {
        List<File> ringtones = new ArrayList<>();
        for (File f : getImportedFiles(context)) {
            if (f.getName().endsWith(".ogg"))
                ringtones.add(f);
        }
        return ringtones;
    }

    /**
     * Checks if a selected style matches an imported custom ringtone name.
     */
    public static File getCustomRingtoneFile(Context context, String styleName) {
        List<File> imported = getImportedRingtones(context);
        for (File f : imported) {
            if (f.getName().equals(styleName)) {
                return f;
            }
        }
        return null; // Not a custom ringtone
    }
    public static File getCustomPatternFile(Context context, String styleName) {
    File dir = new File(context.getFilesDir(), RINGTONE_DIR);
    if (!dir.exists() || !dir.isDirectory())
        return null;

    File candidate = new File(dir, styleName);
    if (candidate.exists() && candidate.isFile() && candidate.getName().endsWith(".csv")) {
        return candidate;
    }
    return null;
}
    public static String loadCSV(File file) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    /**
     * Deletes an imported custom ringtone.
     */
    public static boolean deleteRingtone(Context context, String fileName) {
        File dir = new File(context.getFilesDir(), RINGTONE_DIR);
        File file = new File(dir, fileName);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public static String getFileNameFromUri(Context context, Uri uri) {
        String fileName = "imported_" + System.currentTimeMillis();
        try (android.database.Cursor cursor =
                        context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex =
                        cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    String name = cursor.getString(nameIndex);
                    if (name != null && !name.isEmpty()) {
                        fileName = name;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (fileName != null && fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        return fileName;
    }

    public static String cleanStyleName(String name) {
        if (name == null || name.isEmpty())
            return "Unknown";
        String clean = name;
        if (clean.startsWith("imported_")) {
            clean = clean.substring(9);
            if (clean.matches("\\d{10,}_.*")) {
                int firstUnder = clean.indexOf('_');
                if (firstUnder != -1)
                    clean = clean.substring(firstUnder + 1);
            }
        }
        if (clean.startsWith("native_")) {
            clean = clean.substring(7);
        }
        clean = clean.replace(".ogg", "").replace(".csv", "").replace("_", " ").trim();

        // Capitalize each word
        StringBuilder sb = new StringBuilder();
        String[] words = clean.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (words[i].length() > 0) {
                sb.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    sb.append(words[i].substring(1).toLowerCase());
                }
                if (i < words.length - 1)
                    sb.append(" ");
            }
        }
        return sb.toString();
    }
}
