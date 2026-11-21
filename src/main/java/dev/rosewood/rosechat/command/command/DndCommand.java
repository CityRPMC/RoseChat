package dev.rosewood.rosechat.command.command;

import dev.rosewood.rosechat.chat.PlayerData;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosechat.message.RosePlayer;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;

public class DndCommand extends RoseChatCommand {

    public DndCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("dnd")
                .aliases("donotdisturb")
                .descriptionKey("command-dnd-description")
                .permission("rosechat.dnd")
                .playerOnly(true)
                .build();
    }

    @Override
    protected boolean hasPriority() {
        return true;
    }

    @RoseExecutable
    public void execute(CommandContext context) {
        RosePlayer player = new RosePlayer(context.getSender());
        PlayerData data = player.getPlayerData();
        if (data == null)
            return;

        boolean next = !data.isDnd();
        data.setDnd(next);
        data.save();

        player.sendLocaleMessage(next ? "command-dnd-enabled" : "command-dnd-disabled");
    }

}
