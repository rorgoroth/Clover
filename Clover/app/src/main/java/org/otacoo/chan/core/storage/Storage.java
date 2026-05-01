/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.otacoo.chan.core.storage;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Pair;

import org.otacoo.chan.core.settings.ChanSettings;
import org.otacoo.chan.core.settings.StringSetting;
import org.otacoo.chan.ui.activity.ActivityResultHelper;
import org.otacoo.chan.utils.IOUtils;
import org.otacoo.chan.utils.Logger;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Abstraction of the storage APIs available in Android. It's generally a mess because it
 * juggles between the old file api for legacy support and the newer SAF APIs, which have
 * different semantics in different Android APIs.
 * <p>
 * This is used primarily for saving images, especially on removable storage.
 *
 * The Android Storage Access Framework is used from Android 5.0 and higher. Since Android 5.0
 * it has support for granting permissions for a directory, which we want to save our files to.
 * <p>
 * Otherwise a fallback is provided for only saving on the primary volume with the older APIs.
 */
@Singleton
public class Storage {
    private static final String TAG = "Storage";

    private static final String DEFAULT_DIRECTORY_NAME = "Clover";

    private static final Pattern REPEATED_UNDERSCORES_PATTERN = Pattern.compile("_+");
    private static final Pattern SAFE_CHARACTERS_PATTERN = Pattern.compile("[^a-zA-Z0-9._]");
    private static final int MAX_RENAME_TRIES = 500;

    private Context applicationContext;
    private ActivityResultHelper results;

    private final StringSetting saveLocation;
    private final StringSetting saveLocationTreeUri;
    private String saveLocationTreeUriFolder;

    public static String filterName(String name) {
        name = name.replace(' ', '_');
        name = SAFE_CHARACTERS_PATTERN.matcher(name).replaceAll("");
        name = REPEATED_UNDERSCORES_PATTERN.matcher(name).replaceAll("_");
        if (name.length() == 0) {
            name = "_";
        }
        return name;
    }

    @Inject
    public Storage(Context applicationContext, ActivityResultHelper results) {
        this.applicationContext = applicationContext;
        this.results = results;

        saveLocation = ChanSettings.saveLocation;
        saveLocationTreeUri = ChanSettings.saveLocationTreeUri;
    }

    // Settings controller:
    public String currentStorageName() {
        String uriString = saveLocationTreeUri.get();
        if (uriString == null || uriString.isEmpty()) {
            return saveLocation.get();
        }
        Uri treeUri = Uri.parse(uriString);
        String name = queryTreeName(treeUri);
        return name != null ? name : saveLocation.get();
    }

    // For the settings controller:

    /**
     * Starts up a screen where the user can select a location on their storage.
     * If the user accepts the location, the uri is persisted (so that we have access
     * in the future). The callback is called if everything goes right and we have
     * persistable access to the picked uri.
     *
     * @param handled called if it was picked and persisted.
     */
    public void startOpenTreeIntentAndHandle(StoragePreparedCallback handled) {
        Intent openTreeIntent = getOpenTreeIntent();
        results.getResultFromIntent(openTreeIntent, (resultCode, result) -> {
            if (resultCode == Activity.RESULT_OK) {
                String uri = handleOpenTreeIntent(result);
                if (uri != null) {
                    ChanSettings.saveLocationTreeUri.set(uri);
                    if (handled != null) {
                        handled.onPrepared();
                    }
                }
            }
        });
    }

    public void prepareForSave(String[] folders, StoragePreparedCallback callback) {
        if (saveLocationTreeUri.get().isEmpty() || !hasPermission(saveLocationTreeUri.get())) {
            startOpenTreeIntentAndHandle(callback::onPrepared);
        } else {
            callback.onPrepared();
        }
    }

    /**
     * Creates the save sub-directory for {@code folders} via SAF and updates
     * {@link #saveLocationTreeUriFolder} so that {@link #obtainStorageFileForName} can use it.
     * <p><b>Must be called on a background thread</b> — it performs ContentProvider I/O
     * ({@code listTree} and optionally {@code createDocument}).
     *
     * @return {@code true} on success, {@code false} if the directory could not be created.
     */
    public boolean prepareFolderInBackground(String[] folders) {
        if (folders == null || folders.length == 0) {
            saveLocationTreeUriFolder = null;
            return true;
        }
        saveLocationTreeUriFolder = createDirectoryForSafUri(
                rootDocUri(Uri.parse(saveLocationTreeUri.get())), folders);
        return saveLocationTreeUriFolder != null;
    }

    private boolean hasPermission(String treeUri) {
        Uri uri = Uri.parse(treeUri);
        for (android.content.UriPermission perm :
                applicationContext.getContentResolver().getPersistedUriPermissions()) {
            if (perm.getUri().equals(uri)
                    && perm.isReadPermission()
                    && perm.isWritePermission()) {
                return true;
            }
        }
        return false;
    }

    private Intent getOpenTreeIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
//        intent.addCategory(Intent.CATEGORY_OPENABLE);
        return intent;
    }

    private String handleOpenTreeIntent(Intent intent) {
        boolean read = (intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0;
        boolean write = (intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;

        if (!read) {
            Logger.e(TAG, "No grant read uri permission given");
            return null;
        }

        if (!write) {
            Logger.e(TAG, "No grant write uri permission given");
            return null;
        }

        Uri uri = intent.getData();
        if (uri == null) {
            Logger.e(TAG, "intent.getData() == null");
            return null;
        }

        Logger.i(TAG, "handle open (" + uri.toString() + ")");

        Uri docUri = rootDocUri(uri);
        Logger.i(TAG, "docUri = " + docUri);

        ContentResolver contentResolver = applicationContext.getContentResolver();
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        contentResolver.takePersistableUriPermission(uri, flags);

        return uri.toString();
    }

    private String createDirectoryForSafUri(Uri uri, String[] folders) {
        Uri subTree = null;

        ContentResolver contentResolver = applicationContext.getContentResolver();
        try {
           List<Pair<String, String>> files = listTree(uri);
            boolean createSubdir = true;
            for (Pair<String, String> file : files) {
                if (file.second.equals(folders[0])) {
                    subTree = DocumentsContract.buildDocumentUriUsingTree(uri, file.first);
                    createSubdir = false;
                    break;
                }
            }
            if (createSubdir) {
                subTree = DocumentsContract.createDocument(
                        contentResolver, uri,
                        DocumentsContract.Document.MIME_TYPE_DIR, folders[0]);
            }
        } catch (FileNotFoundException e) {
            Logger.e(TAG, "Could not create subdir", e);
        }

        if (subTree == null) {
            return null;
        } else if (folders.length > 1) {
            return createDirectoryForSafUri(subTree, Arrays.copyOfRange(folders, 1, folders.length));
        } else {
            return subTree.toString();
        }
    }

    public StorageFile obtainStorageFileForName(String[] folders, String name) {
        boolean hasSubfolder = (folders != null && folders.length > 0);
        String uriString = hasSubfolder ? saveLocationTreeUriFolder : saveLocationTreeUri.get();
        if (uriString == null || uriString.isEmpty()) {
            return null;
        }

        ContentResolver contentResolver = applicationContext.getContentResolver();

        // saveLocationTreeUriFolder is an exact document URI of the subdirectory.
        // saveLocationTreeUri may be a raw tree URI or a root document URI; rootDocUri() normalises both.
        Uri targetDocUri = hasSubfolder
                ? Uri.parse(uriString)
                : rootDocUri(Uri.parse(uriString));

        Logger.i(TAG, "saving to " + targetDocUri);

        String finalName = name;
        Uri docUri;
        try {
            int fileNumberSuffix = 0;
            List<Pair<String, String>> list = listTree(targetDocUri);
            out:
            while (true) {
                for (Pair<String, String> file : list) {
                    if (file.second.equalsIgnoreCase(finalName)) {
                        finalName = fileNameWithSuffix(name, ++fileNumberSuffix);
                        continue out;
                    }
                }
                break;
            }

            docUri = DocumentsContract.createDocument(contentResolver, targetDocUri, "text", finalName);
        } catch (FileNotFoundException e) {
            Logger.e(TAG, "obtainStorageFileForName createDocument", e);
            return null;
        }

        if (docUri == null) {
            Logger.e(TAG, "obtainStorageFileForName: createDocument returned null for " + finalName);
            return null;
        }

        return StorageFile.fromUri(contentResolver, docUri, finalName);
    }

    private String fileNameWithSuffix(String name, int suffix) {
        if (suffix == 0) {
            return name;
        } else {
            int lastDot = name.lastIndexOf('.');
            if (lastDot > 0) {
                return name.substring(0, lastDot) + " (" + suffix + ")" + name.substring(lastDot);
            } else {
                return name + " (" + suffix + ")";
            }
        }
    }

    /**
     * Builds the root document URI from either a raw tree URI ({@code tree/X}) or a document URI
     * already rooted at that tree ({@code tree/X/document/Y}). {@code getTreeDocumentId} extracts
     * {@code X} from both formats, so this normalises the two representations.
     */
    private static Uri rootDocUri(Uri treeOrDocUri) {
        return DocumentsContract.buildDocumentUriUsingTree(
                treeOrDocUri, DocumentsContract.getTreeDocumentId(treeOrDocUri));
    }

    private String queryTreeName(Uri treeUri) {
        ContentResolver contentResolver = applicationContext.getContentResolver();

        // Must query the document URI, not the raw tree URI — querying the tree URI directly
        // causes a SecurityException ("obtain access using ACTION_OPEN_DOCUMENT or related APIs").
        Uri docUri = rootDocUri(treeUri);

        Cursor c = null;
        try {
            c = contentResolver.query(docUri, new String[]{
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
            }, null, null, null);

            String name = null;
            if (c != null && c.moveToNext()) {
                name = c.getString(0);
            }
            return name;
        } catch (SecurityException e) {
            return null;
        } catch (Exception e) {
            Logger.e(TAG, "queryTreeName", e);
        } finally {
            IOUtils.closeQuietly(c);
        }

        return null;
    }

    private List<Pair<String, String>> listTree(Uri tree) {
        ContentResolver contentResolver = applicationContext.getContentResolver();

        String documentId = DocumentsContract.getDocumentId(tree);

        try (Cursor c = contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId),
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (c == null) return Collections.emptyList();

            List<Pair<String, String>> result = new ArrayList<>();
            while (c.moveToNext()) {
                result.add(new Pair<>(c.getString(0), c.getString(1)));
            }
            return result;
        }
    }

    public interface StoragePreparedCallback {
        void onPrepared();
    }
}
