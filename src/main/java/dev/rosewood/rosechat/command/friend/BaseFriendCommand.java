package dev.rosewood.rosechat.command.friend;

import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.BaseRoseCommand;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;

public class BaseFriendCommand extends BaseRoseCommand {

    public BaseFriendCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("friend")
                .aliases("f", "friends")
                .permission("rosechat.friend")
                .playerOnly(true)
                .arguments(ArgumentsDefinition.builder()
                        .optionalSub(
                                new FriendHelpCommand(this.rosePlugin, this),
                                new FriendAddCommand(this.rosePlugin),
                                new FriendAcceptCommand(this.rosePlugin),
                                new FriendDenyCommand(this.rosePlugin),
                                new FriendRemoveCommand(this.rosePlugin),
                                new FriendListCommand(this.rosePlugin)
                        ))
                .build();
    }

    @RoseExecutable
    public void execute(CommandContext context) {
        RosePlayer player = new RosePlayer(context.getSender());
        PlayerData data = player.getPlayerData();
        if (data != null && !data.getFriends().isEmpty()) {
            FriendListCommand.displayFriendList(player);
            return;
        }

        FriendHelpCommand.displayHelpMessage(this, context);
    }

}
