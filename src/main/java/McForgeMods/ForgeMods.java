package McForgeMods;

import McForgeMods.commandes.CommandeDepot;
import McForgeMods.commandes.CommandeInstall;
import McForgeMods.commandes.CommandeUpdate;
import McForgeMods.commandes.Show;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.Sources;
import picocli.CommandLine;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(name = "forgemods", showDefaultValues = true, mixinStandardHelpOptions = true,
		subcommands = {Show.class, CommandeDepot.class, CommandeInstall.class, CommandeUpdate.class})
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
	
	@CommandLine.Command(name = "add-repository")
	public int ajout_repo(@CommandLine.Parameters(index = "0", arity = "1..n", paramLabel = "url") List<String> urls,
			@CommandLine.Option(names = {"-d", "--depot"}) Path depot) {
		final DepotLocal depotLocal = new DepotLocal(depot);
		Sources sources;
		
		final File fichier = depotLocal.dossier.resolve("sources.txt").toFile();
		if (fichier.exists()) {
			try (FileInputStream input = new FileInputStream(fichier)) {
				sources = new Sources(input);
			} catch (IOException ignored) {
				return -1;
			}
		} else {
			sources = new Sources();
		}
		
		try (FileOutputStream output = new FileOutputStream(fichier, true);
			 OutputStreamWriter bos = new OutputStreamWriter(output);
			 BufferedWriter bw = new BufferedWriter(bos)) {
			for (String url : urls) {
				try {
					URL u = new URL(url);
					if (!sources.urls().containsKey(u)) {
						bw.newLine();
						if (u.getPath().endsWith(".tar"))
							bw.write("tar\t");
						else
							bw.write("dir\t");
						bw.write(u.toString());
					}
				} catch (MalformedURLException m) {
					System.err.println(String.format("MalformedURL: '%s'", url));
				}
			}
		} catch (IOException i) {
			i.printStackTrace();
			return -1;
		}
		
		return 0;
	}
	
	public static void main(String[] args) {
		CommandLine cl = new CommandLine(new ForgeMods());
		System.exit(cl.execute(args));
	}
	
	/**
	 * Options commune aux fonctions utilisant un dépot et une installation minecraft.
	 */
	public static class DossiersOptions {
		/**
		 * Spécifie le dossier de dépot à utiliser.
		 */
		@CommandLine.Option(names = {"-d", "--depot"}, description = "Dépot local à utiliser")
		public Path depot = null;
		
		/**
		 * Spécifie l'installation minecraft à utiliser. Il peut en exister d'autres de ~/.minecraft.
		 */
		@CommandLine.Option(names = {"-m", "--minecraft"}, description = "Dossier minecraft (~/.minecraft)")
		public Path minecraft = null;
	}
}
