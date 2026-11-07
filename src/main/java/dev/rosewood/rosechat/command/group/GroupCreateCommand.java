package dev.rosewood.rosechat.command.group;

import dev.rosewood.rosechat.api.event.group.GroupCreateEvent;
import dev.rosewood.rosechat.api.event.group.GroupPreCreateEvent;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosechat.config.Settings;
import dev.rosewood.rosechat.hook.channel.rosechat.GroupChannel;
import dev.rosewood.rosechat.message.MessageUtils;
import dev.rosewood.rosechat.message.PermissionArea;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosechat.message.MessageRules;
import dev.rosewood.rosechat.message.RoseMessage;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.argument.ArgumentHandlers;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.HexUtils;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import java.util.Locale;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class GroupCreateCommand extends RoseChatCommand {

    public GroupCreateCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("create")
                .descriptionKey("command-gc-create-description")
                .permission("rosechat.group.create")
                .playerOnly(true)
                .arguments(ArgumentsDefinition.builder()
                        .optional("name", ArgumentHandlers.GREEDY_STRING)
                        .build())
                .build();
    }

    @RoseExecutable
    public void execute(CommandContext context) {
        RosePlayer player = new RosePlayer(context.getSender());
        String defaultName = player.getRealName() + "'s GroupChat";
        String id = this.generateUniqueGroupId(player.getRealName());
        this.execute(player, id, defaultName);
    }

    @RoseExecutable
    public void execute(CommandContext context, String name) {
        RosePlayer player = new RosePlayer(context.getSender());
        String id = this.generateUniqueGroupId(name);
        this.execute(player, id, name);
    }

    private String generateUniqueGroupId(String rawName) {
        String stripped = ChatColor.stripColor(HexUtils.colorify(rawName));
        String baseSlug = stripped == null ? "" : stripped.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (baseSlug.isEmpty())
            baseSlug = "group";

        String candidate = baseSlug;
        int suffix = 2;
        while (this.isGroupIdTaken(candidate)) {
            candidate = baseSlug + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean isGroupIdTaken(String id) {
        if (this.getAPI().getGroupChatById(id) != null)
            return true;
        for (String existingId : this.getAPI().getGroupChatIDs()) {
            if (existingId.equalsIgnoreCase(id))
                return true;
        }
        return Settings.ADD_GROUP_CHANNELS_TO_CHANNEL_LIST.get() && this.getAPI().getChannelById(id) != null;
    }

    private void execute(RosePlayer player, String id, String name) {
        int currentGroupCount = this.getAPI().getGroupChats(player.getUUID()).size();
        int maxAmount = player.getMaxGroupChats();
        if (currentGroupCount >= maxAmount) {
            if (maxAmount <= 1 && this.getAPI().getGroupChatByOwner(player.getUUID()) != null) {
                this.getLocaleManager().sendComponentMessage(player, "command-gc-create-fail");
            } else {
                this.getLocaleManager().sendComponentMessage(player, "gc-limit");
            }
            return;
        }

        if (this.isGroupIdTaken(id)) {
            this.getLocaleManager().sendComponentMessage(player, "command-gc-already-exists");
            return;
        }

        if (!MessageUtils.canColor(player, name, PermissionArea.GROUP)) {
            this.getLocaleManager().sendComponentMessage(player, "no-permission");
            return;
        }

        RoseMessage message = RoseMessage.forLocation(player, PermissionArea.GROUP);
        MessageRules rules = new MessageRules().applyAllFilters();

        MessageRules.RuleOutputs outputs = rules.apply(message, name);
        if (outputs.isBlocked()) {
            if (outputs.getWarning() != null) {
                if (outputs.getWarningMessage() != null) {
                    player.send(outputs.getWarningMessage());
                } else {
                    outputs.getWarning().send(player);
                }
            }

            if (Settings.SEND_BLOCKED_MESSAGES_TO_STAFF.get() && outputs.shouldNotifyStaff()) {
                for (Player staffPlayer : Bukkit.getOnlinePlayers()) {
                    if (staffPlayer.hasPermission("rosechat.seeblocked")) {
                        RosePlayer rosePlayer = new RosePlayer(staffPlayer);
                        rosePlayer.sendLocaleMessage("blocked-message",
                                StringPlaceholders.of("player", message.getSender().getName(),
                                        "message", name));
                    }
                }
            }

            return;
        }

        GroupPreCreateEvent event = new GroupPreCreateEvent(player.asPlayer(), id, name);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return;

        name = event.getName();

        GroupChannel group = this.getAPI().createGroupChat(id, player.asPlayer());
        group.setName(name);

        if (player.getPlayerData() != null) {
            player.getPlayerData().setLastGroupChatId(group.getId());
            if (Settings.CAN_JOIN_GROUP_CHANNELS.get())
                player.switchChannel(group, true);
        }

        GroupCreateEvent groupCreateEvent = new GroupCreateEvent(group);
        Bukkit.getPluginManager().callEvent(groupCreateEvent);

        this.getLocaleManager().sendComponentMessage(player, "command-gc-create-success",
                StringPlaceholders.of("name", name));
    }

}
