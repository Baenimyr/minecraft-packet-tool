package McForgeMods;

import McForgeMods.commandes.CommandeDepot;
import McForgeMods.commandes.Show;
import McForgeMods.outils.Dossiers;
import picocli.CommandLine;

@CommandLine.Command(name = "forgemods", showDefaultValues = true, mixinStandardHelpOptions = true,
		subcommands = {Show.class, CommandeDepot.class})
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
	
	@CommandLine.Command(name = "install", description = "Installe un mod et ses dépendances")
	public int installation(@CommandLine.Mixin Dossiers.DossiersOptions dossiers, @CommandLine.Mixin Help help,
			@CommandLine.Parameters(arity = "0..*", description = "Liste d'identifiants") String[] modids,
			@CommandLine.Option(names = {"--dependences"}, negatable = true,
					description = "Installe tous les autres mods nécessaires au bon fonctionnement de l'installation.")
					boolean dependances) {
		
		
		return 1;
	}
	
	public static void main(String[] args) {
		CommandLine cl = new CommandLine(new ForgeMods());
		System.exit(cl.execute(args));
	}
}
