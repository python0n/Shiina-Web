package dev.osunolimits.routes.post.settings.customization;

import dev.osunolimits.main.App;
import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.servlet.MultipartConfigElement;

import net.coobird.thumbnailator.Thumbnails;

public class HandleAvatarChange extends Shiina {

    private MultipartConfigElement multipartConfig;
    private static final String AVATAR_DIR = App.env.get("AVATARFOLDER");
    private static final int MAX_FILE_SIZE = (Integer.parseInt(App.env.get("MAXFILESIZE"))) * 1024 * 1024;
    private static final int MAX_REQUEST_SIZE = (Integer.parseInt(App.env.get("MAXREQUESTSIZE"))) * 1024 * 1024;

    public HandleAvatarChange() {
        multipartConfig = new MultipartConfigElement(".temp/", MAX_REQUEST_SIZE, MAX_REQUEST_SIZE, 1);
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        if (!shiina.loggedIn) {
            res.redirect("/login?path=/settings/customization");
            return "";
        }

        int userId = shiina.user.id;
        req.raw().setAttribute("org.eclipse.jetty.multipartConfig", multipartConfig);

        try {
            if (req.raw().getParts().size() > 0) {
                var part = req.raw().getPart("avatar");
                if (part != null) {
                    String fileName = part.getSubmittedFileName();
                    String lower = (fileName == null) ? "" : fileName.toLowerCase();

                    boolean allowedExt =
                            lower.endsWith(".png") ||
                            lower.endsWith(".jpg") ||
                            lower.endsWith(".jpeg") ||
                            lower.endsWith(".gif");

                    if (fileName != null && allowedExt && part.getSize() <= MAX_FILE_SIZE) {
                        File avatarDir = new File(AVATAR_DIR);
                        if (!avatarDir.exists()) {
                            avatarDir.mkdirs();
                        }

                        // Ensure all old avatar files (PNG, JPG, JPEG, GIF) are deleted
                        File[] files = avatarDir.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                if (file.getName().matches(userId + "\\.(png|jpg|jpeg|gif)")) {
                                    file.delete();
                                }
                            }
                        }

                        if (lower.endsWith(".gif")) {
                            // GIF only for supporters
                            if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.SUPPORTER)) {
                                res.redirect("/settings/customization?error=You need to be a supporter to use GIFs.");
                                return notFound(res, shiina);
                            }

                            Path finalAvatarPath = Path.of(AVATAR_DIR, userId + ".gif");
                            try (InputStream input = part.getInputStream()) {
                                Files.copy(input, finalAvatarPath, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } else {
                            // PNG/JPG/JPEG -> always save as PNG
                            Path finalAvatarPath = Path.of(AVATAR_DIR, userId + ".png");
                            try (InputStream input = part.getInputStream()) {
                                Thumbnails.of(input)
                                        .size(500, 500)
                                        .outputFormat("png")
                                        .toFile(finalAvatarPath.toFile());
                            }
                        }

                        res.header("Cache-Control", "no-cache, no-store, must-revalidate");
                        res.header("Pragma", "no-cache");
                        res.header("Expires", "0");
                        res.redirect("/settings/customization?info=Avatar uploaded successfully! If it didn't update, hit CTRL+F5");
                    } else {
                        res.redirect("/settings/customization?error=Invalid file type or size exceeds limit.");
                    }
                } else {
                    res.redirect("/settings/customization?error=No file uploaded.");
                }
            } else {
                res.redirect("/settings/customization?error=No parts found in the request.");
            }
        } catch (Exception e) {
            res.redirect("/settings/customization?error=Error processing the upload: " + e.getMessage());
        }

        return "";
    }
}
