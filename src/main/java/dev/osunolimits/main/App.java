package dev.osunolimits.main;

import java.sql.SQLException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.osunolimits.common.AppCache;
import dev.osunolimits.main.init.StartupDatabaseTask;
import dev.osunolimits.main.init.StartupOkHttpTask;
import dev.osunolimits.main.init.StartupSetupCronTask;
import dev.osunolimits.main.init.StartupSetupDataTask;
import dev.osunolimits.main.init.StartupSetupMarketTask;
import dev.osunolimits.main.init.StartupSetupRedisTask;
import dev.osunolimits.main.init.StartupTextTask;
import dev.osunolimits.main.init.StartupWebServerTask;
import dev.osunolimits.main.init.cfg.AutorunSQLTask;
import dev.osunolimits.main.init.cfg.RobotJsonConfigTask;
import dev.osunolimits.main.init.cfg.StartupInitCustomizations;
import dev.osunolimits.main.init.engine.StartupTaskRunner;
import dev.osunolimits.main.init.logger.StartupLogConfigTask;
import dev.osunolimits.main.init.logger.StartupLoggerLevelTask;
import dev.osunolimits.models.Action;
import dev.osunolimits.modules.ShiinaDocs;
import dev.osunolimits.modules.cron.CountryLeaderboardTask;
import dev.osunolimits.modules.cron.DatabaseCleanUpTask;
import dev.osunolimits.modules.cron.DonatorCleanUpTask;
import dev.osunolimits.modules.cron.MultiDetectionTask;
import dev.osunolimits.modules.cron.ServerStatsCollectorTask;
import dev.osunolimits.modules.cron.ShiinaRankCache;
import dev.osunolimits.modules.cron.engine.Cron;
import dev.osunolimits.modules.utils.GroupRegistry;
import dev.osunolimits.modules.utils.ShiinaAchievementsSorter;
import dev.osunolimits.modules.utils.ThemeLoader;
import dev.osunolimits.modules.utils.UserInfoCache;
import dev.osunolimits.plugins.PluginLoader;
import dev.osunolimits.plugins.ShiinaRegistry;
import dev.osunolimits.routes.ap.api.PubSubHandler;
import dev.osunolimits.routes.ap.api.RecoverAccount;
import dev.osunolimits.routes.ap.get.Audit;
import dev.osunolimits.routes.ap.get.Bancho;
import dev.osunolimits.routes.ap.get.ChatExplorer;
import dev.osunolimits.routes.ap.get.Commands;
import dev.osunolimits.routes.ap.get.MapRanking;
import dev.osunolimits.routes.ap.get.MapRequests;
import dev.osunolimits.routes.ap.get.ModularSettings;
import dev.osunolimits.routes.ap.get.Multiaccounts;
import dev.osunolimits.routes.ap.get.Start;
import dev.osunolimits.routes.ap.get.Themes;
import dev.osunolimits.routes.ap.get.groups.Groups;
import dev.osunolimits.routes.ap.get.groups.ManageGroup;
import dev.osunolimits.routes.ap.get.groups.ProcessGroup;
import dev.osunolimits.routes.ap.get.system.SystemConnections;
import dev.osunolimits.routes.ap.get.system.SystemCron;
import dev.osunolimits.routes.ap.get.system.SystemNav;
import dev.osunolimits.routes.ap.get.system.SystemPlugins;
import dev.osunolimits.routes.ap.get.system.SystemThreads;
import dev.osunolimits.routes.ap.get.system.SystemView;
import dev.osunolimits.routes.ap.get.users.ApUser;
import dev.osunolimits.routes.ap.get.users.Users;
import dev.osunolimits.routes.ap.post.ChangeSetting;
import dev.osunolimits.routes.ap.post.ChangeTheme;
import dev.osunolimits.routes.ap.post.DenyMapRequest;
import dev.osunolimits.routes.ap.post.HandleMapStatusUpdate;
import dev.osunolimits.routes.ap.post.ProcessManageGroup;
import dev.osunolimits.routes.api.get.GetComments;
import dev.osunolimits.routes.api.get.GetFirstPlaces;
import dev.osunolimits.routes.api.get.GetPlaycountGraph;
import dev.osunolimits.routes.api.get.GetPlayerScores;
import dev.osunolimits.routes.api.get.GetRankCache;
import dev.osunolimits.routes.api.get.Health;
import dev.osunolimits.routes.api.get.Search;
import dev.osunolimits.routes.api.get.auth.GetLastDayPlayerAdmin;
import dev.osunolimits.routes.api.get.auth.HandleBeatmapFavorite;
import dev.osunolimits.routes.api.get.auth.HandleClanAction;
import dev.osunolimits.routes.api.get.auth.HandleClanDisband;
import dev.osunolimits.routes.api.get.auth.HandleClanLeave;
import dev.osunolimits.routes.api.get.auth.HandleClanRequest;
import dev.osunolimits.routes.api.get.auth.HandleOnBoarding;
import dev.osunolimits.routes.api.get.auth.HandleRelationship;
import dev.osunolimits.routes.api.get.image.GetBanner;
import dev.osunolimits.routes.get.Beatmap;
import dev.osunolimits.routes.get.Beatmaps;
import dev.osunolimits.routes.get.Bot;
import dev.osunolimits.routes.get.Leaderboard;
import dev.osunolimits.routes.get.OnBoarding;
import dev.osunolimits.routes.get.User;
import dev.osunolimits.routes.get.UserScore;
import dev.osunolimits.routes.get.MultiplayerMatch;
import dev.osunolimits.routes.get.MatchView;
import dev.osunolimits.routes.get.Analyzer;
import dev.osunolimits.routes.get.MatchList;
import dev.osunolimits.routes.get.auth.Login;
import dev.osunolimits.routes.get.auth.Register;
import dev.osunolimits.routes.get.clans.Clan;
import dev.osunolimits.routes.get.clans.Clans;
import dev.osunolimits.routes.get.clans.ManageClan;
import dev.osunolimits.routes.get.errors.NotFound;
import dev.osunolimits.routes.get.errors.InternalError;
import dev.osunolimits.routes.get.errors.Forbidden;
import dev.osunolimits.routes.get.modular.Home;
import dev.osunolimits.routes.get.modular.ModuleRegister;
import dev.osunolimits.routes.get.modular.ShiinaModule;
import dev.osunolimits.routes.get.modular.home.BigHeader;
import dev.osunolimits.routes.get.modular.home.MoreInfos;
import dev.osunolimits.routes.get.redirect.BeatmapSetRedirect;
import dev.osunolimits.routes.get.redirect.MpRedirect;
import dev.osunolimits.routes.get.settings.Authentication;
import dev.osunolimits.routes.get.settings.Customization;
import dev.osunolimits.routes.get.settings.Data;
import dev.osunolimits.routes.get.settings.Settings;
import dev.osunolimits.routes.get.simple.Recover;
import dev.osunolimits.routes.get.user.Relations;
import dev.osunolimits.routes.post.HandleComment;
import dev.osunolimits.routes.post.HandleLogin;
import dev.osunolimits.routes.post.HandleLogout;
import dev.osunolimits.routes.post.HandleRecovery;
import dev.osunolimits.routes.post.HandleRegister;
import dev.osunolimits.routes.post.settings.auth.HandleTokenDeletion;
import dev.osunolimits.routes.post.settings.customization.HandleAvatarChange;
import dev.osunolimits.routes.post.settings.customization.HandleBannerChange;
import dev.osunolimits.routes.post.settings.customization.HandleClanCreation;
import dev.osunolimits.routes.post.settings.customization.HandleFlagChange;
import dev.osunolimits.routes.post.settings.customization.HandleGfxDeletion;
import dev.osunolimits.routes.post.settings.customization.HandleModeChange;
import dev.osunolimits.routes.post.settings.customization.HandleNameChange;
import dev.osunolimits.routes.post.settings.customization.HandleUserpageChange;
import dev.osunolimits.routes.post.settings.data.HandleAccountDeletion;
import dev.osunolimits.routes.post.settings.data.HandleDataRequest;
import dev.osunolimits.utils.Auth;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.OkHttpClient;
import redis.clients.jedis.JedisPooled;
import dev.osunolimits.routes.get.ReplayList;
import dev.osunolimits.routes.post.HandleReplaySubmit;
import dev.osunolimits.routes.ap.get.ApReplayList;
import dev.osunolimits.routes.ap.get.ApReplayDownload;
import dev.osunolimits.routes.ap.post.HandleReplayAction;

/**
 * shiina - a modern osu! private server frontend for the web
 * By Marc Andre Herpers
 */
public class App {

    public static final Logger log = LoggerFactory.getLogger("Shiina-Web");
    public static final OkHttpClient sharedClient = new OkHttpClient();
    public static final AppCache appCache = new AppCache();

    public static final String appSecret = Auth.generateNewToken();
    
    public static Dotenv loggerEnv;
    public static Dotenv env;
    public static Map<String, Object> customization;

    
    public static JedisPooled jedisPool;
    public static WebServer webServer;

    public static String version = "2.0.0branch";
    public static String dbVersion = "2.0.0";

    public static boolean devMode = false;

    public static Cron cron = new Cron();

    public static void main(String[] args) throws SQLException {
        if (args.length > 0 && args[0].equals("-dev")) {
            devMode = true;
            log.info("Running shiina in development mode, do not use this in production!");
            log.info("Also some stuff will not work when not running a prod instance in the bg!");
        }

        env = Dotenv.configure().ignoreIfMissing().systemProperties().directory(".config/").load();
        loggerEnv = Dotenv.configure().systemProperties().directory(".config/").filename("logger.env").load();
        
        StartupTaskRunner.register(new StartupTextTask());
        StartupTaskRunner.register(new StartupLoggerLevelTask());
        StartupTaskRunner.register(new StartupSetupDataTask());
        StartupTaskRunner.register(new StartupLogConfigTask());
        StartupTaskRunner.register(new StartupDatabaseTask());
        StartupTaskRunner.register(new StartupSetupRedisTask());
        StartupTaskRunner.register(new AutorunSQLTask());

        StartupTaskRunner.register(new StartupInitCustomizations());

        ThemeLoader.loadThemes();

        StartupTaskRunner.register(new StartupWebServerTask());
        StartupTaskRunner.register(new StartupOkHttpTask());

        StartupTaskRunner.register(new RobotJsonConfigTask());
        StartupTaskRunner.register(new StartupSetupCronTask());

        ModuleRegister.registerInternalModule("home", new BigHeader());
        ModuleRegister.registerInternalModule("home", ShiinaModule.fromRawHtml("HomeInfos", "infos", "home/infos.html"));
        ModuleRegister.registerInternalModule("home", new MoreInfos());

        StartupTaskRunner.register(new StartupSetupMarketTask());

        GroupRegistry.revalidate();

        UserInfoCache.populateIfNeeded();

        ShiinaDocs shiinaDocs = null;
        try {
            shiinaDocs = new ShiinaDocs();
        } catch (Exception e) {
            System.out.println("[WARN] ShiinaDocs disabled: " + e);
        }
shiinaDocs.initializeDocs();

        ShiinaAchievementsSorter.initialize();

        cron.registerTimedTask(120, new MultiDetectionTask());
        cron.registerTimedTask(30, new DatabaseCleanUpTask());
        cron.registerTimedTask(30, new CountryLeaderboardTask());
        cron.registerTimedTask(30, new DonatorCleanUpTask());
        cron.registerFixedRateTask(9, 59, new ShiinaRankCache());
        cron.registerTaskEach15Minutes(new ServerStatsCollectorTask());

        WebServer.get("/health", new Health());

        WebServer.get("/", new Home());
        WebServer.get("/beatmaps", new Beatmaps());
        WebServer.get("/leaderboard", new Leaderboard());

        WebServer.get("/clans", new Clans());
        WebServer.get("/clan/:id", new Clan());
        WebServer.get("/clan/:id/settings", new ManageClan());
        WebServer.get("/scores/:id", new UserScore());
        WebServer.get("/b/:id", new Beatmap());
        WebServer.get("/u/1", new Bot());
        WebServer.get("/u/:id", new User());

        WebServer.get("/matches", new MatchList());
        WebServer.get("/analyzer", new Analyzer());
        WebServer.get("/analyzer/:score_id", new Analyzer());
        WebServer.get("/matches/:id", new MatchView());
        WebServer.get("/mp/:id", new MpRedirect());

        WebServer.get("/replays", new ReplayList());
        WebServer.post("/replays/submit", new HandleReplaySubmit());
        WebServer.get("/ap/replays", new ApReplayList());
        WebServer.get("/ap/replays/download", new ApReplayDownload());
        WebServer.post("/ap/replays/action", new HandleReplayAction());

        // Support osu-style /mp<id> URLs (no slash)
        WebServer.before("/mp*", (req, res) -> {
            String pth = req.pathInfo();
            if (pth != null && pth.matches("^/mp\\d+$")) {
                res.redirect("/matches/" + pth.substring(3));
                spark.Spark.halt();
            }
        });

        WebServer.get("/settings", new Settings());

        WebServer.get("/settings/customization", new Customization());
        WebServer.get("/settings/auth", new Authentication());
        WebServer.get("/settings/data", new Data());

        WebServer.get("/friends", new Relations());
        WebServer.post("/settings/auth/sessions/delete", new HandleTokenDeletion());
        WebServer.post("/settings/gfx/delete", new HandleGfxDeletion());
        WebServer.post("/settings/data/export", new HandleDataRequest());
        WebServer.post("/settings/data/delete", new HandleAccountDeletion());
        WebServer.post("/settings/avatar", new HandleAvatarChange());
        WebServer.post("/settings/country", new HandleFlagChange());
        WebServer.post("/settings/name", new HandleNameChange());
        WebServer.post("/settings/mode", new HandleModeChange());
        WebServer.post("/settings/userpage", new HandleUserpageChange());
        WebServer.post("/settings/banner", new HandleBannerChange());
        WebServer.post("/settings/clans/create", new HandleClanCreation());

        WebServer.get("/beatmapset/:id", new BeatmapSetRedirect());

        WebServer.get("/login", new Login());
        WebServer.get("/register", new Register());
        WebServer.post("/login", new HandleLogin());
        WebServer.post("/logout", new HandleLogout());
        WebServer.get("/onboarding", new OnBoarding());

        WebServer.get("/auth/recover", new Recover());
        WebServer.post("/recover", new HandleRecovery());
        WebServer.post("/register", new HandleRegister());

        WebServer.post("/post/comment", new HandleComment());
        WebServer.notFound(new NotFound());
        WebServer.internalServerError(new InternalError());

        WebServer.get("/api/v1/get_ap_players", new GetLastDayPlayerAdmin());
        WebServer.get("/api/v1/get_comments", new GetComments());
        WebServer.get("/api/v1/get_first_places", new GetFirstPlaces());
        WebServer.get("/api/v1/get_player_scores", new GetPlayerScores());
        WebServer.get("/api/v1/get_rank_cache", new GetRankCache());
        WebServer.get("/api/v1/get_playcount_graph", new GetPlaycountGraph());
        WebServer.get("/api/v1/search", new Search());

        WebServer.get("/api/v1/onboarding", new HandleOnBoarding());
        WebServer.get("/api/v1/manage_cl", new HandleClanAction());
        WebServer.get("/api/v1/leave_clan", new HandleClanLeave());
        WebServer.get("/api/v1/join_clan", new HandleClanRequest());
        WebServer.get("/api/v1/update_rel", new HandleRelationship());

        WebServer.post("/api/v1/disband_clan", new HandleClanDisband());
        WebServer.post("/api/v1/handle_favorite", new HandleBeatmapFavorite());

        WebServer.get("/ap/users/recovery", new RecoverAccount());

        WebServer.get("/ap/bancho", new Bancho());
        WebServer.get("/ap/api/handler", new PubSubHandler());
        WebServer.get("/ap/settings", new ModularSettings());

        WebServer.get("/ap/maprequests", new MapRequests());
        WebServer.get("/ap/mapranking", new MapRanking());

        WebServer.get("/ap/multiaccs", new Multiaccounts());
        WebServer.get("/ap/audit", new Audit());
        WebServer.get("/ap/chat", new ChatExplorer());
        WebServer.get("/ap/start", new Start());
        WebServer.get("/ap/commands", new Commands());
        WebServer.get("/ap/themes", new Themes());
        WebServer.get("/ap/system", new SystemView());
        WebServer.get("/ap/system/cons", new SystemConnections());
        WebServer.get("/ap/system/threads", new SystemThreads());
        WebServer.get("/ap/system/nav", new SystemNav());
        WebServer.get("/ap/system/plugins", new SystemPlugins());
        WebServer.get("/ap/system/cron", new SystemCron());
        WebServer.get("/ap/groups", new Groups());
        WebServer.get("/ap/groups/create", new ManageGroup(Action.CREATE));
        WebServer.get("/ap/groups/edit", new ManageGroup(Action.EDIT));
        WebServer.get("/ap/groups/delete", new ManageGroup(Action.DELETE));
        WebServer.post("/ap/groups/manage", new ProcessManageGroup());
        WebServer.get("/ap/groups/process", new ProcessGroup());
        WebServer.get("/ap/users", new Users());
        WebServer.get("/ap/user", new ApUser());
        WebServer.post("/ap/themes/change", new ChangeTheme());
        WebServer.post("/ap/settings/update", new ChangeSetting());
        WebServer.post("/ap/maprequests/deny", new DenyMapRequest());
        WebServer.post("/ap/mapranking", new HandleMapStatusUpdate());

        WebServer.get("/banner/:id", new GetBanner());

        ShiinaRegistry.registerIconToSettingsGroup("shiina", "fa-solid fa-flask");
        ShiinaRegistry.registerIconToSettingsGroup("donator", "fa-solid fa-users");

        PluginLoader pluginLoader = new PluginLoader();
        pluginLoader.loadPlugins();

        ModuleRegister.reloadModuleConfigurations();

        Runtime.getRuntime().addShutdownHook(new Shutdown());
    }

}
