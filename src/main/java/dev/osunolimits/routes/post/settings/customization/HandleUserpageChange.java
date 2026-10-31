package dev.osunolimits.routes.post.settings.customization;

import dev.osunolimits.utils.Visibility;
import java.sql.ResultSet;

import org.kefirsf.bb.BBProcessorFactory;
import org.kefirsf.bb.TextProcessor;
import org.owasp.encoder.Encode;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import spark.Request;
import spark.Response;

public class HandleUserpageChange extends Shiina {

    @Override
    public Object handle(Request req, Response res) throws Exception {
       ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (!shiina.loggedIn) {
            return redirect(res, shiina, "/login?path=/settings/customization");
        }
        // restricted players can still play, but not change their profile or interact with others
        if (Visibility.isRestricted(shiina.user.priv)) {
            return redirect(res, shiina, "/settings?error=Restricted accounts cannot change their profile");
        }
        int userId = shiina.user.id;
        String userpage = req.queryParams("userpage");

        if(userpage == null || userpage.isEmpty()) {
            return redirect(res, shiina, "/settings?error=Userpage cannot be empty");
        }
        
        if(userpage.length() > 10000) {
            return redirect(res, shiina, "/settings?error=Userpage is too long (max 10000 characters)");
        }

        
        String raw = Encode.forHtmlContent(userpage);
        BBProcessorFactory processor = BBProcessorFactory.getInstance();
        TextProcessor bb = processor.create();
    
        userpage = bb.process(raw);

        ResultSet userpageRs = shiina.mysql.Query("SELECT * FROM `userpages` WHERE `user_id` = ?", userId);
        if(userpageRs.next()) {
            shiina.mysql.Exec("UPDATE `userpages` SET `html` = ?, `raw` = ?, `raw_type` = ? WHERE `user_id` = ?", userpage, raw, "tiptap", userId);
        }else {
            shiina.mysql.Exec("INSERT INTO `userpages` (`user_id`, `html`, `raw`, `raw_type`) VALUES (?, ?, ?, ?)", userId, userpage, raw, "tiptap");
        }

        return redirect(res, shiina, "/settings/customization?info=Userpage was changed");
    }
}
