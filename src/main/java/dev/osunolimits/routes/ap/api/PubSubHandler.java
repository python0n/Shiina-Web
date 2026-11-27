package dev.osunolimits.routes.ap.api;

import java.sql.ResultSet;
import java.util.Set;

import dev.osunolimits.modules.Shiina;
import dev.osunolimits.modules.ShiinaRoute;
import dev.osunolimits.modules.ShiinaRoute.ShiinaRequest;
import dev.osunolimits.modules.pubsubs.SyncedAction;
import dev.osunolimits.modules.utils.AuditLogger;
import dev.osunolimits.plugins.events.admin.OnAddDonorEvent;
import dev.osunolimits.utils.osu.PermissionHelper;
import spark.Request;
import spark.Response;

public class PubSubHandler extends Shiina {

    public enum MessageType {
        RANK,
        RESTRICT,
        UNRESTRICT,
        WIPE,
        ALERT_ALL,
        GIVEDONATOR,
        ADDPRIV,
        REMOVEPRIV,
        NONE,
        RMPB,
        RMUP,
        RECOVERY_LINK
    }

    // bancho bits: MODERATOR | ADMINISTRATOR | DEVELOPER
    private static final int STAFF_BITS = 4096 | 8192 | 16384;
    private static final Set<String> KNOWN_PRIVS = Set.of("normal", "verified", "whitelisted", "supporter",
            "premium", "alumni", "tournament", "nominator", "mod", "admin", "developer");
    // only a developer gives or takes these ("normal" away = restriction without the restrict log)
    private static final Set<String> DEVELOPER_ONLY_PRIVS = Set.of("normal", "mod", "admin", "developer");

    /**
     * TSG-09: staff accounts (mod, admin, developer) can only be managed by a developer.
     * That includes your own account, so an admin can't raise or clear himself either.
     * Returns null when the action is allowed, otherwise the reason.
     */
    static String denyTarget(ShiinaRequest shiina, int targetId) throws Exception {
        if (PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.DEVELOPER)) {
            return null;
        }
        ResultSet rs = shiina.mysql.Query("SELECT `priv` FROM `users` WHERE `id` = ?", targetId);
        if (rs == null || !rs.next()) {
            return "user not found";
        }
        if ((rs.getInt("priv") & STAFF_BITS) != 0) {
            return "only a developer can manage staff accounts";
        }
        return null;
    }

    private static String denyPrivs(ShiinaRequest shiina, String[] privs) {
        if (privs == null || privs.length == 0) {
            return "no privileges given";
        }
        boolean developer = PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.DEVELOPER);
        for (String p : privs) {
            if (p == null || !KNOWN_PRIVS.contains(p)) {
                return "unknown privilege";
            }
            if (!developer && DEVELOPER_ONLY_PRIVS.contains(p)) {
                return "only a developer can give or take staff privileges";
            }
        }
        return null;
    }

    private Object deny(Response res, ShiinaRequest shiina, String reason) throws Exception {
        res.status(403);
        return raw(res, shiina, reason);
    }

    /** AUD-12: bancho refused or didn't answer, so nothing is logged as done */
    private Object failed(Response res, ShiinaRequest shiina, String result) throws Exception {
        res.status(409);
        return raw(res, shiina, result);
    }

    public Object handle(Request req, Response res) throws Exception {
        ShiinaRequest shiina = new ShiinaRoute().handle(req, res);

        if (shiina.user == null || !shiina.loggedIn) {
            return redirect(res, shiina, "/login");
        }

        if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.STAFF)) {
            return redirect(res, shiina, "/");
        }

        MessageType type = MessageType.NONE;
        if (req.queryParams("type") != null) {
            try {
                type = MessageType.valueOf(req.queryParams("type").toUpperCase());
            } catch (Exception e) {
                type = MessageType.NONE;
            }
        }

        if (type == MessageType.NONE) {
            return raw(res, shiina, "invalid type");
        }

        AuditLogger auditLogger = new AuditLogger(shiina.mysql, type);
        
        int userId;
        int mode;
        String reason;

        switch (type) {
            case RESTRICT: {
                if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.MODERATOR)) {
                    return redirect(res, shiina, "/");
                }
               int adminId = shiina.user.id;
                reason = req.queryParams("reason");
                userId = Integer.parseInt(req.queryParams("id"));
                
                String denied = denyTarget(shiina, userId);
                if (denied != null) {
                    return deny(res, shiina, denied);
                }

                String result = SyncedAction.restrict(userId, adminId, reason);
                if (!SyncedAction.SUCCESS.equals(result)) {
                    return failed(res, shiina, result);
                }
                auditLogger.restrictUser(shiina.user, userId,reason);
                break;
            }
            case UNRESTRICT: {
                if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.MODERATOR)) {
                    return redirect(res, shiina, "/");
                }
                int adminId = shiina.user.id;
                reason = req.queryParams("reason");
                userId = Integer.parseInt(req.queryParams("id"));
                
                String denied = denyTarget(shiina, userId);
                if (denied != null) {
                    return deny(res, shiina, denied);
                }

                String result = SyncedAction.unrestrict(userId, adminId, reason);
                if (!SyncedAction.SUCCESS.equals(result)) {
                    return failed(res, shiina, result);
                }
                
                auditLogger.unrestrictUser(shiina.user, userId, reason);
                break;
            }
            case WIPE: {
                if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.MODERATOR)) {
                    return redirect(res, shiina, "/");
                }
                userId = Integer.parseInt(req.queryParams("id"));
                mode = Integer.parseInt(req.queryParams("mode"));
                reason = req.queryParams("reason");

                String denied = denyTarget(shiina, userId);
                if (denied != null) {
                    return deny(res, shiina, denied);
                }

                String result = SyncedAction.wipe(userId, mode);
                if (!SyncedAction.SUCCESS.equals(result)) {
                    return failed(res, shiina, result);
                }

                auditLogger.wipeUser(shiina.user, userId, mode, reason);
                break;
            }
            case ALERT_ALL: {
                if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.MODERATOR)) {
                    return redirect(res, shiina, "/");
                }
                String message = req.queryParams("message");
                
                String result = SyncedAction.alertAll(message);
                if (!SyncedAction.SUCCESS.equals(result)) {
                    return failed(res, shiina, result);
                }

                auditLogger.alertAll(shiina.user, message);
                break;
            }
            case GIVEDONATOR: {
                if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.ADMINISTRATOR)) {
                    return redirect(res, shiina, "/");
                }
                userId = Integer.parseInt(req.queryParams("id"));
                String duration = req.queryParams("duration");
                
                String result = SyncedAction.addDonatorStatus(userId, duration);
                if (!SyncedAction.SUCCESS.equals(result)) {
                    return failed(res, shiina, result);
                }

                new OnAddDonorEvent(duration, userId, shiina.user.id).callListeners();
                auditLogger.giveDonator(shiina.user, userId, duration);
                res.redirect("/ap/user?id=" + userId);
                break;
            }
            case ADDPRIV: {
                if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.ADMINISTRATOR)) {
                    return redirect(res, shiina, "/");
                }
                userId = Integer.parseInt(req.queryParams("id"));
                String[] privs = req.queryParamsValues("privs");
                String denied = denyTarget(shiina, userId);
                if (denied == null) {
                    denied = denyPrivs(shiina, privs);
                }
                if (denied != null) {
                    return deny(res, shiina, denied);
                }

                String result = SyncedAction.addPriv(userId, privs, shiina.user.id);
                if (!SyncedAction.SUCCESS.equals(result)) {
                    return failed(res, shiina, result);
                }
              
                auditLogger.addPriv(shiina.user, userId, privs);
                res.redirect("/ap/user?id=" + userId);
                break;
            }
            case REMOVEPRIV: {
                if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.ADMINISTRATOR)) {
                    return redirect(res, shiina, "/");
                }
                userId = Integer.parseInt(req.queryParams("id"));
                String[] privs = req.queryParamsValues("privs");
                String denied = denyTarget(shiina, userId);
                if (denied == null) {
                    denied = denyPrivs(shiina, privs);
                }
                if (denied != null) {
                    return deny(res, shiina, denied);
                }

                String result = SyncedAction.removePriv(userId, privs, shiina.user.id);
                if (!SyncedAction.SUCCESS.equals(result)) {
                    return failed(res, shiina, result);
                }

                auditLogger.removePriv(shiina.user, userId, privs);
                res.redirect("/ap/user?id=" + userId);
                break;
            }

            case RMUP: {
                if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.MODERATOR)) {
                    return redirect(res, shiina, "/");
                }
                userId = Integer.parseInt(req.queryParams("id"));
                
                String denied = denyTarget(shiina, userId);
                if (denied != null) {
                    return deny(res, shiina, denied);
                }

                SyncedAction.removeUserpage(userId);

                auditLogger.removeUserpage(shiina.user, userId, req.queryParams("reason"));
                res.redirect("/ap/user?id=" + userId);
                break;
            }

            case RMPB: {
                if (!PermissionHelper.hasPrivileges(shiina.user.priv, PermissionHelper.Privileges.MODERATOR)) {
                    return redirect(res, shiina, "/");
                }
                userId = Integer.parseInt(req.queryParams("id"));
                String reasonPb = req.queryParams("reason");

                String denied = denyTarget(shiina, userId);
                if (denied != null) {
                    return deny(res, shiina, denied);
                }

                SyncedAction.removeProfilePicture(userId);
                
                auditLogger.removeProfilePicture(shiina.user, userId, reasonPb);
                res.redirect("/ap/user?id=" + userId);
                break;
            }

            default:
                // RECOVERY_LINK and NONE are not actions of this endpoint
                return raw(res, shiina, "invalid type");
        }

        return raw(res, shiina, "success");
    }
}
