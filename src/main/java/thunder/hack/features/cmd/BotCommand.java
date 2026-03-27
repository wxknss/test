package thunder.hack.features.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import thunder.hack.features.modules.misc.BotManager;

public class BotCommand extends Command {
    public BotCommand() {
        super("bot");
    }

    @Override
    public void executeBuild(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("add").then(argument("nick", word()).executes(ctx -> {
            String name = ctx.getArgument("nick", String.class);
            if (BotManager.getInstance() != null) BotManager.getInstance().addBot(name);
            return 1;
        })));

        builder.then(literal("remove").then(argument("nick", word()).executes(ctx -> {
            String name = ctx.getArgument("nick", String.class);
            if (BotManager.getInstance() != null) BotManager.getInstance().removeBot(name);
            return 1;
        })));

        builder.then(literal("say").then(argument("message", greedyString()).executes(ctx -> {
            String msg = ctx.getArgument("message", String.class);
            if (BotManager.getInstance() != null) BotManager.getInstance().sayAll(msg);
            return 1;
        })));
    }
}
