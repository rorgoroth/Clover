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
package org.otacoo.chan.ui.activity;

import static org.otacoo.chan.Chan.inject;
import static org.otacoo.chan.utils.AndroidUtils.runOnUiThread;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import org.otacoo.chan.core.manager.ReplyManager;
import org.otacoo.chan.utils.AndroidUtils;
import org.otacoo.chan.utils.IOUtils;
import org.otacoo.chan.utils.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

public class ImagePickDelegate implements Runnable {
    private static final String TAG = "ImagePickActivity";

    private static final int IMAGE_PICK_RESULT = 2;
    private static final long MAX_FILE_SIZE = 15 * 1024 * 1024;
    private static final String DEFAULT_FILE_NAME = "file";
    private static final String PREF_PICKER_KEY = "preference_reply_picker_target";
    private static final String PREF_PICKER_LABEL = "preference_reply_picker_target_label";

    @Inject
    ReplyManager replyManager;

    private Activity activity;

    private ImagePickCallback callback;
    private Uri uri;
    private List<Uri> uris;
    private String fileName;
    private boolean success = false;
    private File cacheFile;
    private boolean allowMultiple = false;
    private int maxFileCount = 1;
    private int pendingCount = 0;
    private int completedCount = 0;

    @SuppressWarnings("this-escape")
    public ImagePickDelegate(Activity activity) {
        this.activity = activity;
        inject(this);
    }

    public boolean pick(ImagePickCallback callback) {
        return pick(callback, false, 1);
    }

    public boolean pick(ImagePickCallback callback, boolean allowMultiple) {
        return pick(callback, allowMultiple, 1);
    }

    public boolean pick(ImagePickCallback callback, boolean allowMultiple, int maxFileCount) {
        if (this.callback != null) {
            return false;
        } else {
            this.callback = callback;
            this.allowMultiple = allowMultiple;
            this.maxFileCount = maxFileCount;

            List<PickerTarget> pickerTargets = buildPickerTargets();
            if (pickerTargets.isEmpty()) {
                Logger.e(TAG, "No activity found to get file with");
                callback.onFilePickError(false);
                reset();
                return false;
            }

            PickerTarget preferredTarget = findPreferredTarget(pickerTargets);
            if (preferredTarget != null) {
                activity.startActivityForResult(preferredTarget.intent, IMAGE_PICK_RESULT);
                return true;
            }

            if (pickerTargets.size() == 1) {
                activity.startActivityForResult(pickerTargets.get(0).intent, IMAGE_PICK_RESULT);
                return true;
            }

            showPickerSelectionDialog(pickerTargets);
            return true;
        }
    }

    public static String getPreferredPickerLabel() {
        return AndroidUtils.getPreferences().getString(PREF_PICKER_LABEL, "");
    }

    public static void clearPreferredPickerChoice() {
        AndroidUtils.getPreferences().edit()
                .remove(PREF_PICKER_KEY)
                .remove(PREF_PICKER_LABEL)
                .apply();
    }

    private Intent buildContentIntent() {
        Intent contentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        contentIntent.setType("*/*");

        if (allowMultiple) {
            contentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        return contentIntent;
    }

    private List<PickerTarget> buildPickerTargets() {
        Map<String, PickerTarget> pickerTargets = new LinkedHashMap<>();

        addResolvedTargets(pickerTargets, buildContentIntent(), activity.getString(org.otacoo.chan.R.string.reply_picker_source_files), "files");

        if (!allowMultiple) {
            addResolvedTargets(pickerTargets,
                    new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                    activity.getString(org.otacoo.chan.R.string.reply_picker_source_images),
                    "images");
            addResolvedTargets(pickerTargets,
                    new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
                    activity.getString(org.otacoo.chan.R.string.reply_picker_source_videos),
                    "videos");
        }

        return new ArrayList<>(pickerTargets.values());
    }

    private void addResolvedTargets(Map<String, PickerTarget> pickerTargets, Intent baseIntent,
                                    String sourceLabel, String sourceId) {
        PackageManager packageManager = activity.getPackageManager();
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(baseIntent, 0);

        for (ResolveInfo resolveInfo : resolveInfos) {
            ComponentName componentName = new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
            Intent targetedIntent = new Intent(baseIntent);
            targetedIntent.setComponent(componentName);

            String appLabel = resolveInfo.loadLabel(packageManager).toString();
            String displayLabel = appLabel + " - " + sourceLabel;
            String key = sourceId + "|" + componentName.flattenToShortString();

            if (!pickerTargets.containsKey(key)) {
                pickerTargets.put(key, new PickerTarget(key, displayLabel, targetedIntent));
            }
        }
    }

    private PickerTarget findPreferredTarget(List<PickerTarget> pickerTargets) {
        SharedPreferences preferences = AndroidUtils.getPreferences();
        String preferredTargetKey = preferences.getString(PREF_PICKER_KEY, "");
        if (preferredTargetKey == null || preferredTargetKey.isEmpty()) {
            return null;
        }

        for (PickerTarget pickerTarget : pickerTargets) {
            if (preferredTargetKey.equals(pickerTarget.key)) {
                return pickerTarget;
            }
        }

        clearPreferredPickerChoice();
        return null;
    }

    private void showPickerSelectionDialog(List<PickerTarget> pickerTargets) {
        CharSequence[] pickerLabels = new CharSequence[pickerTargets.size()];
        for (int i = 0; i < pickerTargets.size(); i++) {
            pickerLabels[i] = pickerTargets.get(i).label;
        }

        final int[] selectedIndex = {0};
        new AlertDialog.Builder(activity)
                .setTitle(org.otacoo.chan.R.string.reply_pick_with)
                .setSingleChoiceItems(pickerLabels, 0, (dialog, which) -> selectedIndex[0] = which)
                .setPositiveButton(org.otacoo.chan.R.string.reply_picker_use_once, (dialog, which) ->
                        activity.startActivityForResult(pickerTargets.get(selectedIndex[0]).intent, IMAGE_PICK_RESULT))
                .setNeutralButton(org.otacoo.chan.R.string.reply_picker_always, (dialog, which) -> {
                    PickerTarget pickerTarget = pickerTargets.get(selectedIndex[0]);
                    AndroidUtils.getPreferences().edit()
                            .putString(PREF_PICKER_KEY, pickerTarget.key)
                            .putString(PREF_PICKER_LABEL, pickerTarget.label)
                            .apply();
                    activity.startActivityForResult(pickerTarget.intent, IMAGE_PICK_RESULT);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    callback.onFilePickError(true);
                    reset();
                })
                .setOnCancelListener(dialog -> {
                    callback.onFilePickError(true);
                    reset();
                })
                .show();
    }

    private static class PickerTarget {
        private final String key;
        private final String label;
        private final Intent intent;

        private PickerTarget(String key, String label, Intent intent) {
            this.key = key;
            this.label = label;
            this.intent = intent;
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (callback == null) {
            return;
        }

        boolean ok = false;
        boolean cancelled = false;
        if (requestCode == IMAGE_PICK_RESULT) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                if (allowMultiple) {
                    // Handle multiple files with limit enforcement
                    uris = new ArrayList<>();
                    
                    // Check for ClipData (multiple files)
                    ClipData clipData = data.getClipData();
                    if (clipData != null) {
                        for (int i = 0; i < clipData.getItemCount() && uris.size() < maxFileCount; i++) {
                            uris.add(clipData.getItemAt(i).getUri());
                        }
                    } else {
                        // Fallback: single file even in multi-select mode
                        Uri singleUri = data.getData();
                        if (singleUri != null) {
                            uris.add(singleUri);
                        }
                    }
                    
                    if (!uris.isEmpty()) {
                        // Cap the number of files to process at maxFileCount
                        int filesToProcess = Math.min(uris.size(), maxFileCount);
                        pendingCount = filesToProcess;
                        completedCount = 0;
                        callback.onFilePickLoading();
                        
                        // Process each file in a separate thread (limited by maxFileCount)
                        for (int i = 0; i < filesToProcess; i++) {
                            processFileAtIndex(i);
                        }
                        ok = true;
                    }
                } else {
                    // Handle single file (original behavior)
                    uri = data.getData();

                    Cursor returnCursor = activity.getContentResolver().query(uri, null, null, null, null);
                    if (returnCursor != null) {
                        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        returnCursor.moveToFirst();
                        if (nameIndex > -1) {
                            fileName = returnCursor.getString(nameIndex);
                        }

                        returnCursor.close();
                    }

                    if (fileName == null) {
                        // As per the comment on OpenableColumns.DISPLAY_NAME:
                        // If this is not provided then the name should default to the last segment of the file's URI.
                        fileName = uri.getLastPathSegment();
                    }

                    if (fileName == null) {
                        fileName = DEFAULT_FILE_NAME;
                    }

                    callback.onFilePickLoading();

                    new Thread(this).start();
                    ok = true;
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                cancelled = true;
            }
        }

        if (!ok) {
            callback.onFilePickError(cancelled);
            reset();
        }
    }
    
    private void processFileAtIndex(int index) {
        new Thread(() -> {
            Uri fileUri = uris.get(index);
            File cacheFile = replyManager.getPickFile();
            
            String fileName = DEFAULT_FILE_NAME;
            Cursor returnCursor = activity.getContentResolver().query(fileUri, null, null, null, null);
            if (returnCursor != null) {
                int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                returnCursor.moveToFirst();
                if (nameIndex > -1) {
                    fileName = returnCursor.getString(nameIndex);
                }
                returnCursor.close();
            }
            
            if (fileName == null || fileName.equals(DEFAULT_FILE_NAME)) {
                fileName = fileUri.getLastPathSegment();
                if (fileName == null) {
                    fileName = DEFAULT_FILE_NAME;
                }
            }
            
            ParcelFileDescriptor fileDescriptor = null;
            InputStream is = null;
            OutputStream os = null;
            boolean success = false;
            try {
                fileDescriptor = activity.getContentResolver().openFileDescriptor(fileUri, "r");
                is = new FileInputStream(fileDescriptor.getFileDescriptor());
                os = new FileOutputStream(cacheFile);
                boolean fullyCopied = IOUtils.copy(is, os, MAX_FILE_SIZE);
                if (fullyCopied) {
                    success = true;
                }
            } catch (IOException | SecurityException e) {
                Logger.e(TAG, "Error copying file from the file descriptor", e);
            } finally {
                if (fileDescriptor != null) {
                    try {
                        fileDescriptor.close();
                    } catch (IOException ignored) {
                    }
                }
                IOUtils.closeQuietly(is);
                IOUtils.closeQuietly(os);
            }

            if (!success) {
                if (!cacheFile.delete()) {
                    Logger.e(TAG, "Could not delete picked_file after copy fail");
                }
            }

            final String finalFileName = fileName;
            final File finalCacheFile = cacheFile;
            final boolean finalSuccess = success;
            runOnUiThread(() -> {
                if (finalSuccess) {
                    callback.onFilePicked(finalFileName, finalCacheFile);
                }
                
                completedCount++;
                if (completedCount == pendingCount) {
                    // All files processed
                    reset();
                }
            });
        }).start();
    }

    @Override
    public void run() {
        cacheFile = replyManager.getPickFile();

        ParcelFileDescriptor fileDescriptor = null;
        InputStream is = null;
        OutputStream os = null;
        try {
            fileDescriptor = activity.getContentResolver().openFileDescriptor(uri, "r");
            is = new FileInputStream(fileDescriptor.getFileDescriptor());
            os = new FileOutputStream(cacheFile);
            boolean fullyCopied = IOUtils.copy(is, os, MAX_FILE_SIZE);
            if (fullyCopied) {
                success = true;
            }
        } catch (IOException | SecurityException e) {
            Logger.e(TAG, "Error copying file from the file descriptor", e);
        } finally {
            // FileDescriptor isn't closeable on API 15
            if (fileDescriptor != null) {
                try {
                    fileDescriptor.close();
                } catch (IOException ignored) {
                }
            }
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(os);
        }

        if (!success) {
            if (!cacheFile.delete()) {
                Logger.e(TAG, "Could not delete picked_file after copy fail");
            }
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (success) {
                    callback.onFilePicked(fileName, cacheFile);
                } else {
                    callback.onFilePickError(false);
                }
                reset();
            }
        });
    }

    private void reset() {
        callback = null;
        cacheFile = null;
        success = false;
        fileName = null;
        uri = null;
        uris = null;
        allowMultiple = false;
        maxFileCount = 1;
        pendingCount = 0;
        completedCount = 0;
    }

    public interface ImagePickCallback {
        void onFilePickLoading();

        void onFilePicked(String fileName, File file);

        void onFilePickError(boolean cancelled);
    }
}
