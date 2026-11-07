package dev.rosewood.rosechat.hook.channel.rosechat;

import dev.rosewood.rosechat.RoseChat;
import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.api.event.message.MessageReceivedEvent;
import dev.rosewood.rosechat.api.event.message.PostParseMessageEvent;
import dev.rosewood.rosechat.api.event.message.PreParseMessageEvent;
import dev.rosewood.rosechat.chat.channel.Channel;
import dev.rosewood.rosechat.chat.channel.ChannelMessageOptions;
import dev.rosewood.rosechat.config.Settings;
import dev.rosewood.rosechat.hook.channel.ChannelProvider;
import dev.rosewood.rosechat.hook.channel.rosechat.condition.ConditionalChannel;
import dev.rosewood.rosechat.manager.LocaleManager;
import dev.rosewood.rosechat.message.DeletableMessage;
import dev.rosewood.rosechat.message.MessageDirection;
import dev.rosewood.rosechat.message.MessageUtils;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosechat.message.tokenizer.MessageOutputs;
import dev.rosewood.rosechat.message.tokenizer.composer.ChatComposer;
import dev.rosewood.rosechat.message.MessageRules;
import dev.rosewood.rosechat.message.contents.MessageContents;
import dev.rosewood.rosechat.message.RoseMessage;
import dev.rosewood.rosechat.placeholder.CustomPlaceholder;
import dev.rosewood.rosechat.placeholder.condition.PlaceholderCondition;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

public class RoseChatChannel extends ConditionalChannel implements Spyable {

    // Channel Settings
    protected int radius;
    protected boolean autoJoin;
    protected boolean visibleAnywhere;
    protected boolean joinable;
    protected boolean keepFormatOverBungee;
    protected List<String> worlds;
    protected List<String> servers;

    public RoseChatChannel(ChannelProvider provider) {
        super(provider);
    }

    @Override
    public void onLoad(String id, ConfigurationSection config) {
        super.onLoad(id, config);

        // Set settings specifically for RoseChat Channels
        this.radius = config.contains("radius") ? config.getInt("radius") : -1;
        this.autoJoin = config.contains("auto-join") && config.getBoolean("auto-join");
        this.visibleAnywhere = config.contains("visible-anywhere") && config.getBoolean("visible-anywhere");
        this.joinable = !config.contains("joinable") || config.getBoolean("joinable");
        this.keepFormatOverBungee = config.contains("keep-format") && config.getBoolean("keep-format");
        this.worlds = config.contains("worlds") ? config.getStringList("worlds") : new ArrayList<>();
        this.servers = config.contains("servers") ? config.getStringList("servers") : new ArrayList<>();
    }

     // Auto Join Functions

    @Override
    public boolean onLogin(RosePlayer player) {
        return this.getJoinCondition(player) &&
                (this.autoJoin &&
                        (this.worlds.isEmpty() || this.worlds.contains(player.asPlayer().getWorld().getName())));
    }

    @Override
    public boolean onWorldJoin(RosePlayer player, World from, World to) {
        if (this.worlds.isEmpty() || !this.autoJoin)
            return false;

        // No point in joining again if the new world is linked too.
        if ((from != null && this.worlds.contains(from.getName())) && this.worlds.contains(to.getName()))
            return false;

        // Join the channel if the world is linked to the channel.
        return this.getJoinCondition(player) && this.worlds.contains(to.getName());
    }

    @Override
    public boolean onWorldLeave(RosePlayer player, World from, World to) {
        if (this.worlds.isEmpty() || !this.autoJoin)
            return false;

        // No point in leaving if the new world is linked too.
        if (this.worlds.contains(from.getName()) && this.worlds.contains(to.getName()))
            return false;

        // Leave the channel if the world is linked to the channel.
        return this.worlds.contains(from.getName());
    }

     // Recipient Functions

    /**
     * Retrieves a list of players who should receive messages when visible-anywhere is enabled.
     * @param sender The {@link RosePlayer} who is sending the message.
     * @param world The {@link World} to get the players from, will be null if the world is not needed.
     * @return A {@link List<Player>} of recipients.
     */
    public List<Player> getVisibleAnywhereRecipients(RosePlayer sender, World world) {
        List<Player> recipients = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (world != null && !world.equals(player.getWorld()))
                continue;

            if (this.getReceiveCondition(sender, new RosePlayer(player)))
                recipients.add(player);
        }

        return recipients;
    }

    /**
     * Retrieves a list of players who should receive messages when visible-anywhere is not enabled.
     * @param sender The {@link RosePlayer} who is sending the message.
     * @param world The {@link World} to get the players from, will be null if the world is not needed.
     * @return A {@link List<Player>} of recipients.
     */
    public List<Player> getMemberRecipients(RosePlayer sender, World world) {
        List<Player> recipients = new ArrayList<>();

        for (UUID member : this.members) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && (world == null || world.equals(player.getWorld())))
                recipients.add(player);
        }

        return recipients;
    }

    @Override
    public Set<Player> getIntendedRecipients(RosePlayer sender, boolean includeSpies) {
        Set<Player> recipients = new HashSet<>();
        if (sender.isPlayer())
            recipients.add(sender.asPlayer());

        if (this.radius != -1 && sender.isPlayer()) {
            Location location = sender.asPlayer().getLocation();
            World world = location.getWorld();
            if (world == null)
                return new HashSet<>();

            double radiusSq = (double) this.radius * this.radius;
            List<Player> candidates = this.visibleAnywhere
                    ? this.getVisibleAnywhereRecipients(sender, world)
                    : this.getMemberRecipients(sender, world);
            for (Player player : candidates) {
                if (world.equals(player.getWorld()) && player.getLocation().distanceSquared(location) <= radiusSq)
                    recipients.add(player);
            }
        } else {
            if (this.visibleAnywhere)
                recipients.addAll(this.getVisibleAnywhereRecipients(sender, null));
            else
                recipients.addAll(this.getMemberRecipients(sender, null));
        }

        if (includeSpies)
            recipients.addAll(this.getSpies((player) -> true));

        return recipients;
    }

    // Send Functions

    @Override
    public void send(ChannelMessageOptions options) {
        if (this.handleSlowmode(options))
            return;

        RoseChatAPI api = RoseChatAPI.getInstance();

        // This message is likely sent from another server.
        if (options.messageId() != null) {
            RoseMessage message = RoseMessage.forChannel(options.sender(), this);
            message.setPlayerInput(options.message());
            message.setUUID(options.messageId());
            message.setPlaceholders(this.getInfoPlaceholders().build());

            if (options.isJson()) {
                this.send(options, message, MessageDirection.SERVER_TO_SERVER_RAW, new MessageRules());
            } else {
                // Apply filters if it was sent from another server.
                MessageRules rules = new MessageRules().applyAllFilters();
                this.send(options, message, MessageDirection.SERVER_TO_SERVER, rules);
            }

            return;
        }

        // This message is likely sent from discord to minecraft.
        if (options.wrapper() != null) {
            this.send(options, options.wrapper(), MessageDirection.DISCORD_TO_MINECRAFT, new MessageRules());
            return;
        }

        // If there is no sender, the message should be handled differently.
        if (options.sender() == null) {
            if (api.isBungee())
                this.sendStringToServers(options.message());

            if (this.visibleAnywhere) {
                this.sendStringToVisibleAnywhere(options.message());
            } else if (!this.worlds.isEmpty()) {
                this.sendStringToWorlds(options.message());
            } else {
                this.sendStringToMembers(options.message());
            }

            return;
        }

        // Handle messages to be sent on this server.

        // Return if the channel has a format set, but it is specifically empty.
        String format = this.settings.getFormats().get("chat");
        if (format != null && format.isEmpty())
            return;

        RoseMessage message = RoseMessage.forChannel(options.sender(), this);
        message.setPlaceholders(this.getInfoPlaceholders().build());

        // Apply the rules for this message or return if the message was blocked.
        MessageRules rules = this.applyRules(message, options.message());
        if (rules == null)
            return;

        this.send(options, message, MessageDirection.PLAYER_TO_SERVER, rules);
    }

    // Send To Functions

    private boolean isPlayerVisibleTo(Player viewer, Player target) {
        if (viewer == null || target == null)
            return false;
        if (!viewer.canSee(target))
            return false;
        for (MetadataValue meta : target.getMetadata("vanished")) {
            if (meta.asBoolean())
                return false;
        }
        return true;
    }

    private boolean canReuseParsedMessage(RoseMessage message, String format) {
        if (format == null || message.getSender().isConsole())
            return false;

        if (PreParseMessageEvent.getHandlerList().getRegisteredListeners().length > 0
                || PostParseMessageEvent.getHandlerList().getRegisteredListeners().length > 0)
            return false;

        String input = message.getPlayerInput();
        if (input != null && (input.contains("%other_") || input.contains("%rel_")))
            return false;

        if (format.contains("%other_") || format.contains("%rel_"))
            return false;

        for (CustomPlaceholder placeholder : RoseChatAPI.getInstance().getPlaceholderManager().getPlaceholders().values()) {
            for (PlaceholderCondition condition : placeholder.getPlaceholders().values()) {
                String cond = condition.getCondition();
                if (cond != null && (cond.contains("%other_") || cond.contains("%rel_")))
                    return false;
                for (List<String> list : condition.getValues().values()) {
                    for (String val : list) {
                        if (val != null && (val.contains("%other_") || val.contains("%rel_")))
                            return false;
                    }
                }
            }
        }

        return true;
    }

    private void deliverParsedToReceiver(RoseMessage message, RosePlayer receiver, MessageContents contents,
                                         String deletableJson, boolean rawJsonMode, String discordId) {
        MessageOutputs outputs = contents.outputs();

        receiver.send(contents);

        if (Settings.ENABLE_DELETING_MESSAGES.get() && receiver.getPlayerData() != null && deletableJson != null) {
            DeletableMessage deletableMessage = rawJsonMode
                    ? new DeletableMessage(message.getUUID(), deletableJson, false, null, this.getId())
                    : message.createDeletableMessage(deletableJson, discordId);
            receiver.getPlayerData().getMessageLog().addDeletableMessage(deletableMessage);
        }

        Bukkit.getPluginManager().callEvent(new MessageReceivedEvent(message, contents, receiver));

        if (outputs.getMessage() != null)
            receiver.send(outputs.getMessage());

        if (message.getSender().getUUID() != null && message.getSender().getUUID().equals(receiver.getUUID())) {
            if (!outputs.getServerCommands().isEmpty() || !outputs.getPlayerCommands().isEmpty()) {
                Bukkit.getScheduler().runTask(RoseChat.getInstance(), () -> {
                    for (String command : outputs.getServerCommands())
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                command.replace("%player%", message.getSender().getRealName()));

                    for (String command : outputs.getPlayerCommands())
                        Bukkit.dispatchCommand(message.getSender().isPlayer() ? message.getSender().asPlayer() : Bukkit.getConsoleSender(),
                                command.replace("%player%", message.getSender().getRealName()));
                });
            }
        }

        if (!receiver.isPlayer())
            return;

        if (!outputs.getTaggedPlayers().contains(receiver.getUUID()))
            return;

        if (Settings.SHOW_MENTION_ACTIONBAR.get()
                && (receiver.getPlayerData() == null || receiver.getPlayerData().hasTagSounds())) {
            String rawActionbar = RoseChatAPI.getInstance().getLocaleManager().getMessageOrDefault("mention-actionbar",
                    "<#FFAA00>🔔 <#FFFFCC>%player% <#CCCCFF>mentioned you in <#FF8000>%channel%");
            if (!rawActionbar.isEmpty()) {
                receiver.sendActionBar(RoseChatAPI.getInstance().parse(message.getSender(), receiver,
                        rawActionbar.replace("%player%", message.getSender().getName())
                                .replace("%channel%", this.getId())));
            }
        }

        if ((receiver.getPlayerData() != null && !receiver.getPlayerData().hasTagSounds()) || outputs.getSound() == null)
            return;

        Player player = receiver.asPlayer();
        player.playSound(player.getLocation(), outputs.getSound(), 1.0f, 1.0f);
    }

    private void deliverToBatch(RoseMessage message, List<RosePlayer> recipients, MessageDirection direction,
                                String format, String discordId) {
        if (recipients.isEmpty())
            return;

        boolean enableDeleting = Settings.ENABLE_DELETING_MESSAGES.get();

        if (direction != MessageDirection.SERVER_TO_SERVER_RAW && this.canReuseParsedMessage(message, format)) {
            RosePlayer parseTarget = message.getSender().isPlayer() ? message.getSender() : recipients.get(0);
            MessageContents sharedContents = discordId == null
                    ? message.parse(parseTarget, format)
                    : message.parseMessageFromDiscord(parseTarget, format, discordId);
            String sharedJson = enableDeleting ? sharedContents.build(ChatComposer.json()) : null;

            for (RosePlayer receiver : recipients) {
                this.deliverParsedToReceiver(message, receiver, sharedContents, sharedJson, false, discordId);
            }
            return;
        }

        for (RosePlayer receiver : recipients) {
            if (direction == MessageDirection.SERVER_TO_SERVER_RAW) {
                String jsonMessage = MessageUtils.applyJSONPlaceholders(message.getPlayerInput());
                MessageContents parsedMessage = MessageUtils.parseJSONMessage(receiver, jsonMessage);

                PostParseMessageEvent event = new PostParseMessageEvent(message, receiver,
                        MessageDirection.SERVER_TO_SERVER_RAW, parsedMessage);
                Bukkit.getPluginManager().callEvent(event);

                if (event.isCancelled())
                    continue;

                parsedMessage = event.getContents();
                String deletableJson = enableDeleting
                        ? ChatComposer.json().composeBungee(parsedMessage.buildComponents())
                        : null;
                this.deliverParsedToReceiver(message, receiver, parsedMessage, deletableJson, true, discordId);
            } else {
                PreParseMessageEvent preParseEvent = new PreParseMessageEvent(message, receiver, direction);
                Bukkit.getPluginManager().callEvent(preParseEvent);

                if (preParseEvent.isCancelled())
                    continue;

                MessageContents components = discordId == null
                        ? message.parse(receiver, format)
                        : message.parseMessageFromDiscord(receiver, format, discordId);

                PostParseMessageEvent postParseEvent = new PostParseMessageEvent(message, receiver, direction, components);
                Bukkit.getPluginManager().callEvent(postParseEvent);

                if (postParseEvent.isCancelled())
                    continue;

                MessageContents finalContents = postParseEvent.getContents();
                String deletableJson = enableDeleting ? finalContents.build(ChatComposer.json()) : null;
                this.deliverParsedToReceiver(message, receiver, finalContents, deletableJson, false, discordId);
            }
        }
    }

    private void sendToDiscord(RoseMessage message, MessageDirection direction) {
        if (direction == MessageDirection.SERVER_TO_SERVER && !this.getSettings().shouldSendBungeeToDiscord())
            return;

        if (direction == MessageDirection.DISCORD_TO_MINECRAFT || direction == MessageDirection.SERVER_TO_SERVER_RAW)
            return;

        RoseChatAPI api = RoseChatAPI.getInstance();
        if (api.getDiscord() == null || this.getSettings().getDiscord() == null || !Settings.USE_DISCORD.get())
            return;

        if (this.settings.getFormats().get("minecraft-to-discord") == null ) {
            RoseChat.getInstance().getLogger().warning("Unable to send message to Discord. 'minecraft-to-discord' format could not be found for channel '" + this.getId() + "'");
            return;
        }

        RoseChat.MESSAGE_THREAD_POOL.execute(() ->
                api.getDiscord().sendMessage(message, this, this.getSettings().getDiscord()));
    }

    private void sendToBungee(RoseMessage message, MessageDirection direction) {
        if (direction == MessageDirection.SERVER_TO_SERVER || direction == MessageDirection.SERVER_TO_SERVER_RAW
                || direction == MessageDirection.DISCORD_TO_MINECRAFT)
            return;

        RoseChatAPI api = RoseChatAPI.getInstance();
        if (!api.isBungee())
            return;

        for (String server : this.servers) {
            if (this.keepFormatOverBungee) {
                RoseChat.MESSAGE_THREAD_POOL.execute(() ->
                        api.getBungeeManager().sendChannelMessage(message.getSender(), server, this.getId(), message.getUUID(), true,
                                message.parseBungeeMessage(message.getSender(),
                                                this.getSettings().getFormats().get("chat")).build(ChatComposer.json())));
            } else {
                api.getBungeeManager().sendChannelMessage(message.getSender(), server, this.getId(),
                        message.getUUID(), false, message.getPlayerInput());
            }
        }
    }

    private void sendToConsole(RoseMessage message, MessageDirection direction, String format, String discordId) {
        if (direction != MessageDirection.PLAYER_TO_SERVER && direction != MessageDirection.DISCORD_TO_MINECRAFT)
            return;

        RosePlayer console = new RosePlayer(Bukkit.getConsoleSender());

        PreParseMessageEvent preParseEvent = new PreParseMessageEvent(message, console, direction);
        Bukkit.getPluginManager().callEvent(preParseEvent);
        if (preParseEvent.isCancelled())
            return;

        MessageContents components = direction == MessageDirection.PLAYER_TO_SERVER ?
                message.parse(console, format) :
                message.parseMessageFromDiscord(console, format, discordId);

        PostParseMessageEvent postParseEvent = new PostParseMessageEvent(message, console, direction, components);
        Bukkit.getPluginManager().callEvent(postParseEvent);
        if (postParseEvent.isCancelled())
            return;

        components = postParseEvent.getContents();

        console.send(components);
        Bukkit.getPluginManager().callEvent(new MessageReceivedEvent(message, components, console));
    }

    private boolean collectVisibleAnywhereRecipients(RoseMessage message, List<RosePlayer> regularRecipients, List<RosePlayer> spyRecipients) {
        RosePlayer sender = message.getSender();
        boolean heardByOtherPlayer = false;

        if (this.radius != -1 && sender.isPlayer()) {
            Location location = sender.asPlayer().getLocation();
            World world = location.getWorld();
            if (world == null)
                return false;

            double radiusSq = (double) this.radius * this.radius;
            for (Player player : this.getVisibleAnywhereRecipients(sender, world)) {
                if (sender.getUUID().equals(player.getUniqueId()))
                    continue;

                if (world.equals(player.getWorld()) && player.getLocation().distanceSquared(location) <= radiusSq) {
                    RosePlayer rp = new RosePlayer(player);
                    if (this.canPlayerReceiveMessage(sender, rp)) {
                        regularRecipients.add(rp);
                        if (this.isPlayerVisibleTo(sender.asPlayer(), player))
                            heardByOtherPlayer = true;
                    }
                }
            }

            Predicate<Player> condition = player ->
                    !world.equals(player.getWorld()) || player.getLocation().distanceSquared(location) > radiusSq;
            this.collectSpies(condition, message, spyRecipients);
            return heardByOtherPlayer;
        }

        for (Player player : this.getVisibleAnywhereRecipients(sender, null)) {
            if (sender.isPlayer() && sender.getUUID().equals(player.getUniqueId()))
                continue;

            RosePlayer rp = new RosePlayer(player);
            if (this.canPlayerReceiveMessage(sender, rp))
                regularRecipients.add(rp);
        }

        this.collectSpies(null, message, spyRecipients);
        return true;
    }

    private boolean collectChannelMemberRecipients(RoseMessage message, List<RosePlayer> regularRecipients, List<RosePlayer> spyRecipients) {
        RosePlayer sender = message.getSender();
        boolean heardByOtherPlayer = false;

        if (this.radius != -1 && sender.isPlayer()) {
            Location location = sender.asPlayer().getLocation();
            World world = location.getWorld();
            if (world == null)
                return false;

            double radiusSq = (double) this.radius * this.radius;
            for (Player player : this.getMemberRecipients(sender, world)) {
                if (sender.getUUID().equals(player.getUniqueId()))
                    continue;

                if (world.equals(player.getWorld()) && player.getLocation().distanceSquared(location) <= radiusSq) {
                    RosePlayer rp = new RosePlayer(player);
                    if (this.canPlayerReceiveMessage(sender, rp)) {
                        regularRecipients.add(rp);
                        if (this.isPlayerVisibleTo(sender.asPlayer(), player))
                            heardByOtherPlayer = true;
                    }
                }
            }

            Predicate<Player> condition = player -> !this.members.contains(player.getUniqueId())
                    || !world.equals(player.getWorld())
                    || player.getLocation().distanceSquared(location) > radiusSq;
            this.collectSpies(condition, message, spyRecipients);
            return heardByOtherPlayer;
        } else if (!this.worlds.isEmpty()) {
            for (Player player : this.getMemberRecipients(sender, null)) {
                if (sender.isPlayer() && sender.getUUID().equals(player.getUniqueId()))
                    continue;

                if (!this.worlds.contains(player.getWorld().getName()))
                    continue;

                RosePlayer rp = new RosePlayer(player);
                if (this.canPlayerReceiveMessage(sender, rp))
                    regularRecipients.add(rp);
            }

            Predicate<Player> condition = player -> !this.members.contains(player.getUniqueId())
                    || !this.worlds.contains(player.getWorld().getName());
            this.collectSpies(condition, message, spyRecipients);
            return true;
        } else {
            for (Player player : this.getMemberRecipients(sender, null)) {
                if (sender.isPlayer() && sender.getUUID().equals(player.getUniqueId()))
                    continue;

                RosePlayer rp = new RosePlayer(player);
                if (this.canPlayerReceiveMessage(sender, rp))
                    regularRecipients.add(rp);
            }

            Predicate<Player> condition = player -> !this.members.contains(player.getUniqueId());
            this.collectSpies(condition, message, spyRecipients);
            return true;
        }
    }

    private void collectSpies(Predicate<Player> condition, RoseMessage message, List<RosePlayer> spyRecipients) {
        List<Player> spies = this.getSpies(condition);
        RosePlayer sender = message.getSender();
        for (Player spy : spies) {
            if (spy == null)
                continue;

            if (condition == null && this.members.contains(spy.getUniqueId()))
                continue;

            RosePlayer spyPlayer = new RosePlayer(spy);
            if (this.radius == -1 && spyPlayer.getChannel() == this)
                continue;

            if (sender.getUUID() != null && sender.getUUID().equals(spy.getUniqueId()))
                continue;

            if (this.canPlayerReceiveMessage(sender, spyPlayer))
                spyRecipients.add(spyPlayer);
        }
    }

    private void send(ChannelMessageOptions options, RoseMessage message, MessageDirection direction, MessageRules rules) {
        // Use the format provided in the options if found, or use the "chat" format in the channel settings.
        String format = options.format() != null ? options.format() : this.getSettings().getFormats().get("chat");

        // Send the message to the console if it should be sent in chat.
        this.sendToConsole(message, direction, format, options.discordId());

        // Disable spam filter for further messages.
        rules.ignoreMessageLogging();

        this.sendToDiscord(message, direction);
        this.sendToBungee(message, direction);

        List<RosePlayer> regularRecipients = new ArrayList<>();
        List<RosePlayer> spyRecipients = new ArrayList<>();

        RosePlayer sender = message.getSender();
        if (this.canPlayerReceiveMessage(sender, sender))
            regularRecipients.add(sender);

        boolean heardByOtherPlayer = this.visibleAnywhere
                ? this.collectVisibleAnywhereRecipients(message, regularRecipients, spyRecipients)
                : this.collectChannelMemberRecipients(message, regularRecipients, spyRecipients);

        boolean notifyEmptyRadius = this.radius != -1
                && sender.isPlayer()
                && direction == MessageDirection.PLAYER_TO_SERVER
                && !heardByOtherPlayer
                && Settings.WARN_ON_EMPTY_RADIUS_CHANNEL.get();

        String discordId = options.discordId();
        RoseChat.MESSAGE_THREAD_POOL.execute(() -> {
            this.deliverToBatch(message, regularRecipients, direction, format, discordId);

            if (notifyEmptyRadius) {
                LocaleManager localeManager = RoseChatAPI.getInstance().getLocaleManager();
                String rawWarning = localeManager.getMessageOrDefault("empty-radius-channel",
                        "<#8E99A2>&oNobody is close enough to hear you. Use <#55FFFF>/g<#8E99A2>&o for Global chat.");
                if (!rawWarning.isEmpty()) {
                    RosePlayer consoleSender = new RosePlayer(Bukkit.getConsoleSender());
                    sender.send(RoseChatAPI.getInstance().parse(consoleSender, sender, rawWarning));
                }
            }

            if (!spyRecipients.isEmpty()) {
                this.deliverToBatch(message, spyRecipients, direction, Settings.CHANNEL_SPY_FORMAT.get(), null);
            }
        });
    }

    // Send String Functions

    /**
     * Directly sends a string to the connected servers.
     * @param message The message to send.
     */
    private void sendStringToServers(String message) {
        RoseChatAPI api = RoseChatAPI.getInstance();

        for (String server : this.servers)
            api.getBungeeManager().sendChannelMessage(new RosePlayer("", ""), server, this.getId(), null, false, message);
    }

    /**
     * Directly sends a string to visible-anywhere recipients.
     * @param message The message to send.
     */
    private void sendStringToVisibleAnywhere(String message) {
        // We have to assume this should be directed at all players, as no sender could be provided.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("rosechat.channel." + this.getId()))
                continue;

            player.sendMessage(message);
        }
    }

    /**
     * Directly sends a string to players in the worlds.
     * @param message The message to send.
     */
    private void sendStringToWorlds(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!this.worlds.contains(player.getWorld().getName()))
                continue;

            if (!player.hasPermission("rosechat.channel." + this.getId()))
                continue;

            player.sendMessage(message);
        }
    }

    /**
     * Directly sends a string to channel members.
     * @param message The message to send.
     */
    private void sendStringToMembers(String message) {
        for (UUID uuid : this.members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                player.sendMessage(message);
        }
    }

    /**
     * Generic function for when a player leaves a team.
     * This finds the new channel and adds the player to it.
     */
    public void onTeamLeaveGeneric(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null)
            return;

        RosePlayer rosePlayer = new RosePlayer(player);
        Channel currentChannel = rosePlayer.getChannel();
        if (currentChannel != this)
            return;

        Channel channel = rosePlayer.findChannel();
        boolean success = rosePlayer.switchChannel(channel);

        if (!success)
            return;

        String joinMessage = channel.getSettings().getFormats().get("join-message");
        if (joinMessage != null)
            rosePlayer.send(RoseChatAPI.getInstance().parse(rosePlayer, rosePlayer, joinMessage));
        else
            rosePlayer.sendLocaleMessage("command-channel-joined",
                    StringPlaceholders.of("id", channel.getId()));
    }

    @Override
    public List<UUID> getMembers() {
        return this.members;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public List<String> getServers() {
        return this.servers;
    }

    @Override
    public boolean canJoinByCommand(RosePlayer player) {
        return player.hasPermission("rosechat.channelbypass")
                || (player.hasPermission("rosechat.channel." + this.getId()) && this.joinable && this.getJoinCondition(player));
    }

    @Override
    public StringPlaceholders.Builder getInfoPlaceholders() {
        LocaleManager localeManager = RoseChatAPI.getInstance().getLocaleManager();
        String trueValue = localeManager.getLocaleMessage("command-chat-info-true");
        String falseValue = localeManager.getLocaleMessage("command-chat-info-false");
        String nullValue = localeManager.getLocaleMessage("command-chat-info-none");

        return super.getInfoPlaceholders()
                .add("radius", this.radius == -1 ? nullValue : this.radius)
                .add("discord", this.getSettings().getDiscord() == null ? nullValue : this.getSettings().getDiscord())
                .add("auto-join", this.autoJoin ? trueValue : falseValue)
                .add("visible-anywhere", this.visibleAnywhere ? trueValue : falseValue)
                .add("joinable", this.joinable ? trueValue : falseValue)
                .add("keep-format", this.keepFormatOverBungee ? trueValue : falseValue)
                .add("worlds", this.worlds.isEmpty() ? nullValue : this.worlds.toString())
                .add("servers", this.servers.isEmpty() ? nullValue : this.servers.toString());
    }

}
