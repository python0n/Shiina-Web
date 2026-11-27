package dev.osunolimits.routes.ap.api;

import com.google.gson.Gson;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.pubsubs.SyncedAction;
import dev.osunolimits.modules.utils.AuditLogger;
import dev.osunolimits.routes.ap.api.PubSubHandler.MessageType;
import dev.osunolimits.plugins.events.admin.OnRecoveryTokenGenerated;
import dev.osunolimits.utils.Validation;
import dev.osunolimits.utils.osu.PermissionHelper;
import lombok.Data;
import spark.Request;
import spark.Response;

public class RecoverAccount extends Shiina {
    private static final Gson gson = new Gson();

    @Override
    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (shiina.user == null) {
            return redirect(res, shiina, "/login");
        }

        if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.ADMINISTRATOR)) {
            return redirect(res, shiina, "/");
        }

        int userid = 0;
        if (req.queryParams("id") != null && Validation.isNumeric(req.queryParams("id"))) {
            userid = Integer.parseInt(req.queryParams("id"));
        }

        if (userid == 0)
            return redirect(res, shiina, "/ap/users");

        // TSG-09: a recovery link for a staff account would let an admin take over a developer
        String denied = PubSubHandler.denyTarget(shiina, userid);
        if (denied != null) {
            res.status(403);
            return raw(res, shiina, denied);
        }

        String token = SyncedAction.generateRecoveryToken(userid);

        RecoveryOutput output = new RecoveryOutput();
        output.url = "/auth/recover?token=" + token;

        new OnRecoveryTokenGenerated(userid, token).callListeners();
        // who made a recovery link for whom goes to the audit log and the staff channel
        new AuditLogger(shiina.mysql, MessageType.RECOVERY_LINK).recoveryLink(shiina.user, userid);

        return raw(res, shiina, gson.toJson(output));
    }

    @Data
    public class RecoveryOutput {
        public String url;
    }
}
