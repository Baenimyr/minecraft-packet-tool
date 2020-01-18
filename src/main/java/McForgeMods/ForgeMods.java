package McForgeMods;

import McForgeMods.commandes.CommandeDepot;
import McForgeMods.commandes.CommandeInstall;
import McForgeMods.commandes.Show;
import picocli.CommandLine;

@CommandLine.Command(name = "forgemods", showDefaultValues = true, mixinStandardHelpOptions = true,
		subcommands = {Show.class, CommandeDepot.class, CommandeInstall.class})
public class ForgeMods implements Runnable {
	
	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;
	
	@Override
	public void run() {
		throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand");
	}
	
	public static class Help {
		@CommandLine.Option(names = {"--help"}, usageHelp = true)
		boolean help;
	}
	
	public static void main(String[] args) {
		CommandLine cl = new CommandLine(new ForgeMods());
		System.exit(cl.execute(args));
	}
}
