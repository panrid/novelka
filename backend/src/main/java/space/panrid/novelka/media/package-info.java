/**
 * Pictures people upload: avatars, covers, illustrations, pictures in messages.
 * Every file is decoded, checked, cropped to its shape, resized and re-encoded, so what
 * readers download is always a clean JPEG or PNG without the uploader's metadata.
 */
@ApplicationModule(displayName = "Картинки")
package space.panrid.novelka.media;

import org.springframework.modulith.ApplicationModule;
