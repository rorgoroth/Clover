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
package org.otacoo.chan.core.site;

/**
 * Defines file upload limits for a site.
 * Used to configure maximum file sizes, dimensions, and file counts.
 */
public class FileUploadLimits {
    /**
     * Maximum file size in bytes for a single file upload.
     * -1 means no limit.
     */
    public final long maxFileSize;

    /**
     * Maximum WebM file size in bytes.
     * -1 means no limit or not applicable.
     */
    public final long maxWebmSize;

    /**
     * Maximum width and height for uploaded images in pixels.
     * -1 means no limit.
     */
    public final int maxImageDimensions;

    /**
     * Maximum number of files that can be uploaded in a single post.
     * 1 means only single file uploads are allowed.
     */
    public final int maxFileCount;

    public FileUploadLimits(long maxFileSize, long maxWebmSize, int maxImageDimensions, int maxFileCount) {
        this.maxFileSize = maxFileSize;
        this.maxWebmSize = maxWebmSize;
        this.maxImageDimensions = maxImageDimensions;
        this.maxFileCount = maxFileCount;
    }

    /**
     * Creates default limits with single file uploads and no size restrictions.
     */
    public static FileUploadLimits unlimited() {
        return new FileUploadLimits(-1, -1, -1, 1);
    }

    /**
     * Creates limits with restricted file count only.
     */
    public static FileUploadLimits withFileCount(int maxFileCount) {
        return new FileUploadLimits(-1, -1, -1, maxFileCount);
    }
}
