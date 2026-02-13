package ti4.spring.jda;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.apache.commons.lang3.function.Consumers;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Service;
import ti4.commands.CommandManager;
import ti4.cron.AutoPingCron;
import ti4.cron.CategoryCleanupCron;
import ti4.cron.CloseLaunchThreadsCron;
import ti4.cron.CronManager;
import ti4.cron.EndOldGamesCron;
import ti4.cron.FastScFollowCron;
import ti4.cron.InteractionLogCron;
import ti4.cron.LogButtonRuntimeStatisticsCron;
import ti4.cron.LogCacheStatsCron;
import ti4.cron.LongExecutionHistoryCron;
import ti4.cron.OldUndoFileCleanupCron;
import ti4.cron.ReuploadStaleEmojisCron;
import ti4.cron.SabotageAutoReactCron;
import ti4.cron.TechSummaryCron;
import ti4.cron.UploadRecentStatsCron;
import ti4.cron.UploadStatsCron;
import ti4.cron.WinningPathCron;
import ti4.executors.ExecutorServiceManager;
import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;
import ti4.helpers.TIGLHelper;
import ti4.image.MapRenderPipeline;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.listeners.AutoCompleteListener;
import ti4.listeners.BanListener;
import ti4.listeners.BotRuntimeStatsListener;
import ti4.listeners.ButtonListener;
import ti4.listeners.ChannelCreationListener;
import ti4.listeners.DeletionListener;
import ti4.listeners.MessageListener;
import ti4.listeners.ModalListener;
import ti4.listeners.SelectionMenuListener;
import ti4.listeners.SlashCommandListener;
import ti4.listeners.ThreadCreateListener;
import ti4.listeners.UserJoinServerListener;
import ti4.listeners.UserLeaveServerListener;
import ti4.map.persistence.GameManager;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogBufferManager;
import ti4.migration.DataMigrationManager;
import ti4.selections.SelectionManager;
import ti4.service.draft.SliceGenerationPipeline;
import ti4.service.emoji.ApplicationEmojiService;
import ti4.service.statistics.StatisticsPipeline;
import ti4.settings.GlobalSettings;
import ti4.settings.GlobalSettings.ImplementedSettings;

@RequiredArgsConstructor
@Service
public class JdaService {

    // TODO: Eventually we need to make these non-static and autowire in this service.
    //       Another thought: we may not want to trust any old "Admin" role on a server
    //       should actually have admin rights
    public static final Set<Role> adminRoles = new HashSet<>();
    public static final Set<Role> developerRoles = new HashSet<>();
    public static final Set<Role> bothelperRoles = new HashSet<>();

    public static JDA jda;
    public static String guildPrimaryID;
    public static boolean testingMode;
    public static Guild guildPrimary;
    private static Guild guildSecondary;
    private static Guild guildTertiary;
    private static Guild guildQuaternary;
    private static Guild guildQuinary;
    private static Guild guildSenary;
    private static Guild guildSeptenary;
    private static Guild guildOctonary;
    private static Guild guildNonary;
    private static Guild guildDecenary;
    private static Guild guildUndenary;
    private static Guild guildDuodenary;
    private static Guild guildTredenary;
    private static Guild guildQuadrodenary;
    public static Guild guildFogOfWar;
    public static Guild guildFogOfWarSecondary;
    public static Guild guildCommunityPlays;
    private static Guild guildMegagame;
    private static Guild guildTourney;
    public static final Set<Guild> guilds = new HashSet<>();
    public static final List<Guild> serversToCreateNewGamesOn = new ArrayList<>();
    public static final List<Guild> fowServers = new ArrayList<>();

    private final ApplicationArguments applicationArguments;

    @PostConstruct
    public void init() {
        BotLogger.info("STARTING JDA");
        String[] args = applicationArguments.getSourceArgs();
        jda = JDABuilder.createDefault(args[0])
                // This is a privileged gateway intent that is used to update user information and join/leaves
                // (including kicks). This is required to cache all members of a guild (including chunking)
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                // This is a privileged gateway intent this is only used to enable access to the user content in
                // messages (also including embeds/attachments/components).
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                // not 100 sure this is needed? It may be for the Emoji cache... but do we actually need that?
                .enableIntents(GatewayIntent.GUILD_EXPRESSIONS)
                // It *appears* we need to pull all members or else the bot has trouble pinging players
                // but that may be a misunderstanding, in case we want to try to use an LRU cache in the future
                // and avoid loading every user at startup
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                // This allows us to use our own ShutdownHook, created below
                .setEnableShutdownHook(false)
                .build();

        BotLogger.info("INITIALIZING LISTENERS");
        jda.addEventListener(
                // Priority Listeners First
                new BotRuntimeStatsListener(),
                new MessageListener(),
                new SlashCommandListener(),
                ButtonListener.getInstance(),
                new UserJoinServerListener(),
                new AutoCompleteListener(),
                new BanListener(),
                new ThreadCreateListener(),

                // Non-Priority Listeners
                new DeletionListener(),
                new SelectionMenuListener(),
                new ChannelCreationListener(),
                new UserLeaveServerListener(),
                // ModalListener has a long init time
                ModalListener.getInstance());

        BotLogger.info("AWAITING JDA READY");
        try {
            jda.awaitReady();
        } catch (Throwable t) {
            BotLogger.critical("Error waiting for bot to get ready", t);
            return;
        }

        jda.getPresence()
                .setPresence(OnlineStatus.DO_NOT_DISTURB, Activity.customStatus("STARTING UP: Connecting to Servers"));

        BotLogger.info("INITIALIZING SERVERS");

        // Primary HUB Server
        guildPrimaryID = args[2];
        tryToInitGuild(args[2], false);

        if (guildPrimary == null) {
            BotLogger.critical("Failed to start the bot on the primary guild. Aborting.");
            return;
        }

        // Community Plays TI
        if (args.length >= 4) {
            guildCommunityPlays = tryToInitGuild(args[3], false);
        }

        // Async: FOW Chapter
        if (args.length >= 5) {
            guildFogOfWar = tryToInitGuild(args[4], false);
            if (guildFogOfWar != null) fowServers.add(guildFogOfWar);
        }

        // Async: Stroter's Paradise
        if (args.length >= 6) {
            guildSecondary = tryToInitGuild(args[5], true);
        }

        // Async: Dreadn't
        if (args.length >= 7) {
            guildTertiary = tryToInitGuild(args[6], true);
        }

        // Async: War Sun Tzu
        if (args.length >= 8) {
            guildQuaternary = tryToInitGuild(args[7], true);
        }

        // Async: Fighter Club
        if (args.length >= 9) {
            guildQuinary = tryToInitGuild(args[8], true);
        }

        // Async: Tommer Hawk
        if (args.length >= 10) {
            guildSenary = tryToInitGuild(args[9], true);
        }

        // Async: Duder's Domain
        if (args.length >= 11) {
            guildSeptenary = tryToInitGuild(args[10], true);
        }

        // Async: What's up Dock
        if (args.length >= 12) {
            guildOctonary = tryToInitGuild(args[11], true);
        }

        // Async: Megagame server
        if (args.length >= 13) {
            guildMegagame = tryToInitGuild(args[12], false);
        }

        // Async: Ship Flag
        if (args.length >= 14) {
            guildNonary = tryToInitGuild(args[13], true);
        }

        // Async: FOW Chapter Secondary
        if (args.length >= 15) {
            guildFogOfWarSecondary = tryToInitGuild(args[14], false);
            if (guildFogOfWarSecondary != null) fowServers.add(guildFogOfWarSecondary);
        }

        // Async: Tournament Server 1
        if (args.length >= 16) {
            guildTourney = tryToInitGuild(args[15], false);
        }

        // Async: Great Carrier Reef
        if (args.length >= 17) {
            guildDecenary = tryToInitGuild(args[16], true);
        }

        // Async: PDStrians
        if (args.length >= 18) {
            guildUndenary = tryToInitGuild(args[17], true);
        }

        // Async: Stroaty McStroatface
        if (args.length >= 19) {
            guildDuodenary = tryToInitGuild(args[18], true);
        }

        // Async: Planetary Duck System
        if (args.length >= 20) {
            guildTredenary = tryToInitGuild(args[19], true);
        }

        // Async: Dannel's Camp Ground
        if (args.length >= 21) {
            guildQuadrodenary = tryToInitGuild(args[20], true);
        }

        BotLogger.info("FINISHED INITIALIZING SERVERS\n> "
                + guilds.size() + " total servers connected\n> "
                + serversToCreateNewGamesOn.size() + " Overflow servers for new games\n> "
                + fowServers.size() + " Fog of War servers");

        // Attempt to start a "Search Only" version of the bot on eligible servers
        for (Guild searchGuild : jda.getGuilds()) {
            if (guilds.stream().anyMatch(g -> g.getId().equals(searchGuild.getId()))) continue;
            startBotSearchOnly(searchGuild);
        }

        // Check for and report a missing bot-log webhook
        if (!GlobalSettings.settingExists(ImplementedSettings.BOT_LOG_WEBHOOK_URL)) {
            BotLogger.warning(
                    "BOT-LOG WEBHOOK NOT FOUND for Primary GuildID:" + guildPrimaryID
                            + "\nPlease set a valid bot-log Webhook URL using `/developer setting setting_name:bot_log_webhook_url setting_type:string setting_value:<url>`");
        }

        // LOAD DATA
        BotLogger.info("LOADING DATA");
        jda.getPresence().setActivity(Activity.customStatus("STARTING UP: Loading Data"));
        ApplicationEmojiService.uploadNewEmojis();
        // load all /resources/planets/ and /resources/systems/ .json files, into 3 HashMaps (not 2)
        TileHelper.init();
        // load all /resources/positions/ .properties files, each into 1 Properties
        PositionMapper.init();
        // load all /resources/data/ .json and .properties files, except logging.properties, each into 1 HashMap or
        // Properties
        Mapper.init();
        // load all /resources/alias/ .properties files, except position_alias_old.properties, into
        AliasHandler.init();
        // create directories for games files
        Storage.init();
        SelectionManager.init();
        initializeWhitelistedRoles();
        TIGLHelper.validateTIGLness();

        jda.getPresence().setActivity(Activity.customStatus("STARTING UP: Loading Games"));

        BotLogger.info("LOADING GAMES");
        GameManager.initialize();
        BotLogger.info("FINISHED LOADING GAMES");

        if (DataMigrationManager.runMigrations()) {
            BotLogger.info("FINISHED RUNNING MIGRATIONS");
        }

        // START CRONS
        AutoPingCron.register();
        ReuploadStaleEmojisCron.register();
        LogCacheStatsCron.register();
        WinningPathCron.register();
        UploadStatsCron.register();
        UploadRecentStatsCron.register();
        OldUndoFileCleanupCron.register();
        EndOldGamesCron.register();
        LogButtonRuntimeStatisticsCron.register();
        TechSummaryCron.register();
        SabotageAutoReactCron.register();
        FastScFollowCron.register();
        CloseLaunchThreadsCron.register();
        InteractionLogCron.register();
        LongExecutionHistoryCron.register();
        CategoryCleanupCron.register();

        // BOT IS READY
        GlobalSettings.setSetting(ImplementedSettings.READY_TO_RECEIVE_COMMANDS, true);
        BotLogger.info("BOT IS READY TO RECEIVE COMMANDS");
        updatePresence();
    }

    private static Guild tryToInitGuild(String guildID, boolean addToNewGameServerList) {
        try {
            return initGuild(guildID, addToNewGameServerList);
        } catch (Throwable t) {
            BotLogger.critical("Failed to initialize guild " + guildID + ". Skipping.", t);
            return null;
        }
    }

    private static Guild initGuild(String guildID, boolean addToNewGameServerList) {
        if (!guildID.matches("\\b[0-9]+\\b")) {
            BotLogger.error(
                    "Invalid Guild ID provided: `" + guildID
                            + "` - If this is running in Production, please correct the ID [here](https://github.com/AsyncTI4/TI4_map_generator_bot/settings/variables/actions/GUILDID_LIST)");
            return null;
        }
        Guild guild = jda.getGuildById(guildID);
        if (guild == null) {
            BotLogger.error("JDA FAILED TO FIND GUILD with ID: `" + guildID
                    + "` - please ensure AsyncTI4 is added to that server and has Admin permissions.");
            return null;
        }
        if (!startBot(guild)) {
            BotLogger.error("Failed to start bot for guild: " + guild.getName());
            return null;
        }
        if (addToNewGameServerList) {
            serversToCreateNewGamesOn.add(guild);
        }
        return guild;
    }

    private static boolean startBot(Guild guild) {
        if (guild == null) {
            return false;
        }
        if (guildPrimaryID.equals(guild.getId())) {
            guildPrimary = guild;
            BotLogger.init(); // requires guildPrimary bot-log channel existing
        }
        try {
            CommandListUpdateAction commands = guild.updateCommands();
            CommandManager.getCommands().forEach(command -> command.register(commands));
            commands.queue(Consumers.nop(), BotLogger::catchRestError);
            BotLogger.info("BOT STARTED UP: " + guild.getName());
            guilds.add(guild);
        } catch (Exception e) {
            BotLogger.error("\n# FAILED TO START BOT ", e);
            return false;
        }
        return true;
    }

    private static boolean startBotSearchOnly(Guild guild) {
        // Do not set up search commands for test bots, and definitely never for the hub server, which several test bots
        // are still in
        if (guild == null) return false;
        if (System.getenv("TESTING") != null) return false;

        // Disable this for now
        if (true) return false;

        try {
            CommandListUpdateAction commands = guild.updateCommands();
            CommandManager.getCommands().forEach(command -> command.registerSearchCommands(commands));
            commands.queue(Consumers.nop(), BotLogger::catchRestError);
            BotLogger.info("SEARCH-ONLY BOT STARTED UP: " + guild.getName());
            guilds.add(guild);
        } catch (Exception e) {
            BotLogger.error("\n# SEARCH-ONLY BOT FAILED TO START: " + guild.getName(), e);
        }
        return true;
    }

    public static void updatePresence() {
        long activeGames = GameManager.getActiveGameCount();
        jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.playing(activeGames + " games of Async TI4"));
    }

    /**
     * Initializes the whitelisted roles for the bot, including admin, developer, and bothelper roles.
     * <ul>
     * <li>Admins may execute /admin, /developer, and /bothelper commands</li>
     * <li>Developers may execute /developer commands</li>
     * <li>Bothelpers may execute /bothelper commands</li>
     * </ul>
     *
     * Add your test server's role ID to enable access to these commands on your server
     */
    private static void initializeWhitelistedRoles() {
        // ADMIN ROLES
        var envAdminRoles = System.getenv("ADMIN_ROLES").split(",");
        for (var adminRole : envAdminRoles) {
            adminRoles.add(jda.getRoleById(adminRole));
        }

        adminRoles.removeIf(Objects::isNull);

        // DEVELOPER ROLES

        developerRoles.addAll(adminRoles); // admins may also execute developer commands
        var envDeveloperRoles = System.getenv("DEVELOPER_ROLES").split(",");
        for (var developerRole : envDeveloperRoles) {
            developerRoles.add(jda.getRoleById(developerRole));
        }

        developerRoles.removeIf(Objects::isNull);

        // BOTHELPER ROLES

        bothelperRoles.addAll(developerRoles); // developers may also execute bothelper commands
        bothelperRoles.addAll(adminRoles); // admins can also execute bothelper commands
        var envBothelperRoles = System.getenv("ADMIN_ROLES").split(",");
        for (var bothelperRole : envBothelperRoles) {
            bothelperRoles.add(jda.getRoleById(bothelperRole));
        }

        bothelperRoles.removeIf(Objects::isNull);
    }

    public static String getBotId() {
        return jda.getSelfUser().getId();
    }

    public static boolean isReadyToReceiveCommands() {
        return GlobalSettings.getSetting(
                GlobalSettings.ImplementedSettings.READY_TO_RECEIVE_COMMANDS.toString(), Boolean.class, false);
    }

    public static List<Category> getAvailablePBDCategories() {
        return guilds.stream()
                .flatMap(guild -> guild.getCategories().stream())
                .filter(category -> category.getName().toUpperCase().startsWith("PBD #"))
                .toList();
    }

    public static boolean isValidGuild(String guildId) {
        return guilds.stream().anyMatch(g -> g.getId().equals(guildId));
    }

    @PreDestroy
    public void shutdown() {
        try {
            jda.getPresence().setPresence(OnlineStatus.DO_NOT_DISTURB, Activity.customStatus("BOT IS SHUTTING DOWN"));
            BotLogger.info("SHUTDOWN PROCESS STARTED");
            GlobalSettings.setSetting(ImplementedSettings.READY_TO_RECEIVE_COMMANDS, false);
            BotLogger.info("NO LONGER ACCEPTING COMMANDS");
            if (ExecutorServiceManager.shutdown()) { // will wait for up to an additional 20 seconds
                BotLogger.info("FINISHED PROCESSING ASYNC THREADPOOL");
            } else {
                BotLogger.info("DID NOT FINISH PROCESSING ASYNC THREADPOOL");
            }
            if (MapRenderPipeline.shutdown()) { // will wait for up to an additional 20 seconds
                BotLogger.info("FINISHED RENDERING MAPS");
            } else {
                BotLogger.info("DID NOT FINISH RENDERING MAPS");
            }
            if (SliceGenerationPipeline.shutdown()) { // will wait for up to an additional 20 seconds
                BotLogger.info("FINISHED RENDERING SLICE DRAFTS");
            } else {
                BotLogger.info("DID NOT FINISH RENDERING SLICE DRAFTS");
            }
            if (StatisticsPipeline.shutdown()) { // will wait for up to an additional 20 seconds
                BotLogger.info("FINISHED PROCESSING STATISTICS");
            } else {
                BotLogger.info("DID NOT FINISH PROCESSING STATISTICS");
            }
            CronManager.shutdown(); // will wait for up to an additional 20 seconds
            LogBufferManager.sendBufferedLogsToDiscord(); // will drain the log buffer and doesn't have a timeout
            BotLogger.info("SHUTDOWN PROCESS COMPLETE");
            TimeUnit.SECONDS.sleep(1); // wait for BotLogger
            jda.shutdown();
            jda.awaitShutdown(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            BotLogger.error("Error encountered within shutdown process:\n> ", e);
        }
    }
}
