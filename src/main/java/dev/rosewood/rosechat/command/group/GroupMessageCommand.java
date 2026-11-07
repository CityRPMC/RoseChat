package dev.rosewood.rosechat.command.group;

import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.config.Settings;
import dev.rosewood.rosechat.hook.channel.rosechat.GroupChannel;
import dev.rosewood.rosechat.message.MessageUtils;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.Argument;
import dev.rosewood.rosegarden.command.framework.ArgumentHandler;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.BaseRoseCommand;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.InputIterator;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.entity.Player;

public class GroupMessageCommand extends BaseRoseCommand {

    public GroupMessageCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("gcm")
                .aliases("gcmsg", "gmsg")
                .descriptionKey("command-gcm-description")
                .permission("rosechat.group.message")
                .playerOnly(true)
                .arguments(ArgumentsDefinition.builder()
                        .optional("message", new ArgumentHandler<String>(String.class) {
                            @Override
                            public String handle(CommandContext context, Argument argument, InputIterator inputIterator) throws HandledArgumentException {
                                List<String> inputs = new ArrayList<>();
                                while (inputIterator.hasNext())
                                    inputs.add(inputIterator.next());
                                if (inputs.isEmpty())
                                    throw new HandledArgumentException("argument-handler-string");
                                return String.join(" ", inputs);
                            }

                            @Override
                            public List<String> suggest(CommandContext context, Argument argument, String[] args) {
                                if (args.length <= 1 && context.getSender() instanceof Player player) {
                                    return RoseChatAPI.getInstance().getGroupChats(player.getUniqueId()).stream()
                                            .map(GroupChannel::getId)
                                            .collect(Collectors.toList());
                                }
                                return Collections.emptyList();
                            }
                        })
                        .build())
                .build();
    }

    @RoseExecutable
    public void execute(CommandContext context) {
        RosePlayer player = new RosePlayer(context.getSender());
        List<GroupChannel> groups = RoseChatAPI.getInstance().getGroupChats(player.getUUID());
        if (groups.isEmpty()) {
            player.sendLocaleMessage("no-gc");
            return;
        }

        GroupChannel targetGroup;
        if (player.getPlayerData() != null && player.getPlayerData().isCurrentChannelGroupChannel()
                && player.getChannel() instanceof GroupChannel currentGroup && groups.size() > 1) {
            int idx = groups.indexOf(currentGroup);
            targetGroup = groups.get((idx + 1) % groups.size());
        } else {
            targetGroup = player.getActiveGroupChannel();
        }

        this.switchToGroup(player, targetGroup);
    }

    @RoseExecutable
    public void execute(CommandContext context, String rawInput) {
        RosePlayer player = new RosePlayer(context.getSender());
        String[] parts = rawInput.split(" ", 2);
        GroupChannel matchedGroup = RoseChatAPI.getInstance().getGroupChatById(parts[0]);
        if (matchedGroup != null && matchedGroup.getMembers().contains(player.getUUID())) {
            if (parts.length == 1) {
                this.switchToGroup(player, matchedGroup);
            } else {
                this.sendQuickMessage(player, matchedGroup, parts[1]);
            }
            return;
        }

        GroupChannel activeGroup = player.getActiveGroupChannel();
        if (activeGroup == null) {
            player.sendLocaleMessage("no-gc");
            return;
        }

        this.sendQuickMessage(player, activeGroup, rawInput);
    }

    private void switchToGroup(RosePlayer player, GroupChannel group) {
        if (group == null) {
            player.sendLocaleMessage("no-gc");
            return;
        }

        if (!Settings.CAN_JOIN_GROUP_CHANNELS.get()) {
            player.sendLocaleMessage("message-blank");
            return;
        }

        boolean success = player.switchChannel(group, true);
        if (!success) {
            player.sendLocaleMessage("argument-handler-group");
            return;
        }

        player.sendLocaleMessage("command-channel-joined",
                StringPlaceholders.of("id", group.getName()));
    }

    private void sendQuickMessage(RosePlayer player, GroupChannel group, String message) {
        if (MessageUtils.isMessageEmpty(message)) {
            player.sendLocaleMessage("message-blank");
            return;
        }

        player.quickChat(group, message);
    }

}
