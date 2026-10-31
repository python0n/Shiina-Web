package dev.osunolimits.routes.post.settings.customization;

import dev.osunolimits.utils.Visibility;
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

public class HandleBannerChange extends Shiina {

    private MultipartConfigElement multipartConfig;
    private static final String BANNER_DIR = "data/banners";
    private static final int MAX_FILE_SIZE = (Integer.parseInt(App.env.get("MAXFILESIZE"))) * 1024 * 1024;
    private static final int MAX_REQUEST_SIZE = (Integer.parseInt(App.env.get("MAXREQUESTSIZE"))) * 1024 * 1024;

    public HandleBannerChange() {
        multipartConfig = new MultipartConfigElement(".temp/", MAX_REQUEST_SIZE, MAX_REQUEST_SIZE, 1);
        File bannerDir = new File(BANNER_DIR);
        if (!bannerDir.exists()) {
            bannerDir.mkdirs();
        }
    }

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);
        shiina.data.put("actNav", 0);

        if (!shiina.loggedIn) {
            res.redirect("/login?path=/settings/customization");
            return notFound(res, shiina);
        }
        // restricted players can still play, but not change their profile or interact with others
        if (Visibility.isRestricted(shiina.user.priv)) {
            return redirect(res, shiina, "/settings?error=Restricted accounts cannot change their profile");
        }

        if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.SUPPORTER)) {
            res.redirect("/settings/customization?error=You need to be a supporter to upload banners.");
            return notFound(res, shiina);
        }

        int userId = shiina.user.id;
        req.raw().setAttribute("org.eclipse.jetty.multipartConfig", multipartConfig);

   

        try {
            if (req.raw().getParts().size() > 0) {
                var part = req.raw().getPart("banner");
                if (part != null) {
                    String fileName = part.getSubmittedFileName();

                    if (fileName != null
                            && fileName.toLowerCase().endsWith(".png")
                            && part.getSize() <= MAX_FILE_SIZE) {
                        
                        Path finalBannerPath = Path.of(BANNER_DIR, userId + ".png");

                        try (InputStream input = part.getInputStream()) {
                            Files.copy(input, finalBannerPath, StandardCopyOption.REPLACE_EXISTING);
                        }

                        res.header("Cache-Control", "no-cache, no-store, must-revalidate");
                        res.header("Pragma", "no-cache");
                        res.header("Expires", "0");
                        res.redirect("/settings/customization?info=Banner uploaded successfully! If it didn't update, hit CTRL+F5");
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

        return notFound(res, shiina);
    }
}
