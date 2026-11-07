package dev.rosewood.rosechat.hook.channel.rosechat;

import dev.rosewood.rosechat.RoseChat;
import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.api.event.group.GroupDisbandEvent;
import dev.rosewood.rosechat.api.event.group.GroupJoinEvent;
import dev.rosewood.rosechat.api.event.group.GroupLeaveEvent;
import dev.rosewood.rosechat.api.event.group.GroupNameEvent;
import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.chat.channel.Channel;
import dev.rosewood.rosechat.chat.channel.ChannelMessageOptions;
import dev.rosewood.rosechat.chat.channel.ChannelSettings;
import dev.rosewood.rosechat.config.Settings;
import dev.rosewood.rosechat.manager.GroupManager;
import dev.rosewood.rosechat.message.PermissionArea;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosechat.message.MessageRules;
import dev.rosewood.rosechat.message.RoseMessage;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class GroupChannel extends Channel {

    private String name;
    private UUID owner;

    public GroupChannel(String id) {
        super(null);

        this.id = id;

        this.settings = new ChannelSettings();
        this.getSettings().getFormats().put("chat", Settings.GROUP_FORMAT.get());
    }

    public void save() {
        RoseChat.getInstance().getManager(GroupManager.class).createOrUpdateGroupChat(this);
    }

    @Override
    public void send(ChannelMessageOptions options) {
        // An empty message can't be sent to the channel.
        if (options.message() == null)
            return;

        RoseChatAPI api = RoseChatAPI.getInstance();

        // If there is no sender, the message can't be sent a group channel.
        if (options.sender() == null)
            return;

        // Handle messages to be sent to this group.
        RoseMessage message = RoseMessage.forLocation(options.sender(), PermissionArea.GROUP);
        message.setPlaceholders(this.getInfoPlaceholders().build());

        // Apply the rules for this message or return if the message was blocked.
        MessageRules rules = this.applyRules(message, options.message());
        if (rules == null)
            return;

        String format = options.format() != null ? options.format() : this.getSettings().getFormats().get("chat");

        if (options.sender().isPlayer() && options.sender().getPlayerData() != null)
            options.sender().getPlayerData().setLastGroupChatId(this.id);

        // Get all the members as RosePlayers and add the console.
        List<RosePlayer> recipients = new java.util.ArrayList<>();
        for (UUID memberUuid : this.getMembers()) {
            Player memberPlayer = Bukkit.getPlayer(memberUuid);
            if (memberPlayer == null)
                continue;
            RosePlayer rp = new RosePlayer(memberPlayer);
            if (this.canPlayerReceiveMessage(message.getSender(), rp))
                recipients.add(rp);
        }
        recipients.add(new RosePlayer(Bukkit.getConsoleSender()));

        List<RosePlayer> spyRecipients = new java.util.ArrayList<>();
        for (UUID uuid : api.getPlayerDataManager().getGroupSpies()) {
            if (this.members.contains(uuid))
                continue;

            Player spyPlayer = Bukkit.getPlayer(uuid);
            if (spyPlayer != null)
                spyRecipients.add(new RosePlayer(spyPlayer));
        }

        RoseChat.MESSAGE_THREAD_POOL.execute(() -> {
            for (RosePlayer member : recipients) {
                dev.rosewood.rosechat.message.contents.MessageContents parsed = message.parse(member, format);
                member.send(parsed);

                if (member.isPlayer() && parsed.outputs().getTaggedPlayers().contains(member.getUUID())) {
                    Player p = member.asPlayer();
                    if ((member.getPlayerData() == null || member.getPlayerData().hasTagSounds()) && parsed.outputs().getSound() != null)
                        p.playSound(p.getLocation(), parsed.outputs().getSound(), 1.0f, 1.0f);

                    if (Settings.SHOW_MENTION_ACTIONBAR.get()) {
                        String rawMention = api.getLocaleManager().getMessageOrDefault("mention-actionbar",
                                "<#FF8000>@ <#CCCCFF>%player% <#8E99A2>mentioned you in <#FFAA00>%channel%");
                        if (!rawMention.isEmpty()) {
                            String formattedMention = StringPlaceholders.of(
                                    "player", message.getSender().getName(),
                                    "channel", this.name != null ? this.name : this.id
                            ).apply(rawMention);
                            member.sendActionBar(api.parse(message.getSender(), member, formattedMention));
                        }
                    }
                }
            }

            if (!spyRecipients.isEmpty()) {
                String spyFormat = Settings.GROUP_SPY_FORMAT.get();
                for (RosePlayer spy : spyRecipients)
                    spy.send(message.parse(spy, spyFormat));
            }
        });
    }

    @Override
    public boolean canPlayerReceiveMessage(RosePlayer sender, RosePlayer receiver) {
        if (sender.getUUID() != null && receiver.getUUID() != null)
            if (sender.getUUID().equals(receiver.getUUID()))
                return true;

        PlayerData data = receiver.getPlayerData();
        if (data == null)
            return true;

        if (sender.getUUID() != null)
            return !data.getIgnoringPlayers().contains(sender.getUUID());

        return true;
    }

    private void addMember(UUID uuid) {
        this.members.add(uuid);
    }

    private void removeMember(UUID uuid) {
        this.members.remove(uuid);
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
        return Collections.emptyList();
    }

    @Override
    public boolean canJoinByCommand(RosePlayer player) {
        return Settings.CAN_JOIN_GROUP_CHANNELS.get();
    }

    @Override
    public StringPlaceholders.Builder getInfoPlaceholders() {
        return super.getInfoPlaceholders()
                .add("owner", Bukkit.getOfflinePlayer(this.owner).getName())
                .add("name", this.name)
                .add("group_id", this.getId())
                .add("group", this.getId())
                .add("group_owner", Bukkit.getOfflinePlayer(this.owner).getName())
                .add("group_name", this.name);
    }

    // Group Methods

    public boolean rename(String name) {
        GroupNameEvent event = new GroupNameEvent(this, name);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return false;

        name = event.getName();

        this.setName(name);
        this.save();

        return true;
    }

    public boolean join(UUID uuid) {
        GroupJoinEvent event = new GroupJoinEvent(this, Bukkit.getPlayer(uuid));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return false;

        this.addMember(uuid);
        RoseChatAPI.getInstance().getGroupManager().addMember(this, uuid);
        return true;
    }

    public boolean kick(UUID uuid, boolean wasKicked) {
        GroupLeaveEvent event = new GroupLeaveEvent(this, Bukkit.getOfflinePlayer(uuid), wasKicked);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return false;

        this.removeMember(uuid);
        RoseChatAPI.getInstance().getGroupManager().removeMember(this, uuid);
        return true;
    }

    public boolean disband() {
        GroupDisbandEvent event = new GroupDisbandEvent(this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return false;

        RoseChatAPI.getInstance().deleteGroupChat(this);
        return true;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public UUID getOwner() {
        return this.owner;
    }

    public void setMembers(List<UUID> members) {
        this.members.addAll(members);
    }

}
