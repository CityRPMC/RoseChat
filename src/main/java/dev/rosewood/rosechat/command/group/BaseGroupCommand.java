package dev.rosewood.rosechat.command.group;

import dev.rosewood.rosechat.api.RoseChatAPI;
import dev.rosewood.rosechat.config.Settings;
import dev.rosewood.rosechat.hook.channel.rosechat.GroupChannel;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.BaseRoseCommand;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import java.util.List;

public class BaseGroupCommand extends BaseRoseCommand {

    public BaseGroupCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("group")
                .aliases("gc")
                .arguments(ArgumentsDefinition.builder()
                        .optionalSub(
                                new GroupHelpCommand(this.rosePlugin, this),
                                new GroupAcceptCommand(this.rosePlugin),
                                new GroupCreateCommand(this.rosePlugin),
                                new GroupDenyCommand(this.rosePlugin),
                                new GroupDisbandCommand(this.rosePlugin),
                                new GroupInfoCommand(this.rosePlugin),
                                new GroupInviteCommand(this.rosePlugin),
                                new GroupKickCommand(this.rosePlugin),
                                new GroupLeaveCommand(this.rosePlugin),
                                new GroupListCommand(this.rosePlugin),
                                new GroupMembersCommand(this.rosePlugin),
                                new GroupRenameCommand(this.rosePlugin),
                                new GroupPromoteCommand(this.rosePlugin)
                        ))
                .build();
    }

    @RoseExecutable
    public void execute(CommandContext context) {
        RosePlayer player = new RosePlayer(context.getSender());
        if (player.isPlayer() && Settings.CAN_JOIN_GROUP_CHANNELS.get()) {
            List<GroupChannel> groups = RoseChatAPI.getInstance().getGroupChats(player.getUUID());
            if (!groups.isEmpty()) {
                GroupChannel targetGroup = null;
                if (player.getPlayerData() != null && player.getPlayerData().isCurrentChannelGroupChannel()
                        && player.getChannel() instanceof GroupChannel currentGroup && groups.size() > 1) {
                    int idx = groups.indexOf(currentGroup);
                    targetGroup = groups.get((idx + 1) % groups.size());
                } else {
                    targetGroup = player.getActiveGroupChannel();
                }

                if (targetGroup != null && player.switchChannel(targetGroup, true)) {
                    player.sendLocaleMessage("command-channel-joined",
                            StringPlaceholders.of("id", targetGroup.getName()));
                    return;
                }
            }
        }

        GroupHelpCommand.displayHelpMessage(this, context);
    }

}
