package McForgeMods;

import McForgeMods.commandes.*;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.Sources;
import McForgeMods.solveur.SolveurPaquet;
import picocli.CommandLine;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandLine.Command(name = "forgemods", showDefaultValues = true, resourceBundle = "mcforgemods/lang/ForgeMods",
		mixinStandardHelpOptions = true, version = "0.4.0",
		subcommands = {CommandeShow.class, CommandeListe.class, CommandeDepot.class, CommandeInstall.class,
				CommandeRemove.class, CommandeUpdate.class})
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
		final Sources sources = new Sources();
		
		depotLocal.dossier.toFile().mkdirs();
		final File fichier = depotLocal.dossier.resolve("sources.txt").toFile();
		if (fichier.exists()) {
			try (FileInputStream input = new FileInputStream(fichier)) {
				sources.importation(input);
			} catch (IOException ignored) {
				return -1;
			}
		}
		
		try (FileOutputStream output = new FileOutputStream(fichier, true);
			 OutputStreamWriter bos = new OutputStreamWriter(output); BufferedWriter bw = new BufferedWriter(bos)) {
			for (String url : urls) {
				try {
					URI u = new URI(url);
					if (!sources.urls().contains(u)) {
						bw.newLine();
						bw.write(u.toString());
					}
				} catch (URISyntaxException m) {
					System.err.printf("URISyntax Error: '%s'%n", url);
				}
			}
		} catch (IOException i) {
			i.printStackTrace();
			return -1;
		}
		
		return 0;
	}
	
	@CommandLine.Command(name = "search")
	public int commandeSearch(@CommandLine.Mixin Help help, @CommandLine.Option(names = {"-d", "--depot"}) Path depot,
			@CommandLine.Option(names = {"-e", "--regex"}) boolean regex,
			@CommandLine.Parameters(paramLabel = "search", arity = "1") String recherche) {
		final DepotLocal depotLocal = new DepotLocal(depot);
		try {
			depotLocal.importation();
		} catch (IOException io) {
			System.err.println("Erreur de lecture du dépôt.");
			return 1;
		}
		
		final HashSet<String> modids = new HashSet<>();
		if (regex) {
			Pattern schema = Pattern.compile(recherche, Pattern.CASE_INSENSITIVE);
			for (String modid : depotLocal.getModids()) {
				Stream<Matcher> s_match = Stream.concat(Stream.of(modid),
						depotLocal.getModVersions(modid).stream().map(v -> v.nomCommun).filter(Objects::nonNull))
						.map(schema::matcher);
				if (s_match.anyMatch(Matcher::find)) modids.add(modid);
			}
		} else {
			String recherche_l = recherche.toLowerCase();
			for (String modid : depotLocal.getModids()) {
				if (modid.contains(recherche_l)) {
					modids.add(modid);
				}
			}
			depotLocal.getModids().stream().filter(modid -> modid.contains(recherche)).forEach(modids::add);
		}
		
		modids.stream().sorted(String::compareTo).forEach(modid -> {
			Optional<PaquetMinecraft> modVersion = depotLocal.getModVersions(modid).stream()
					.max(Comparator.comparing(mv -> mv.version));
			modVersion.ifPresent(mod -> System.out.printf("\u001B[32m%s\u001B[0m \"%s\"%n", mod.modid, mod.version));
		});
		
		return 0;
	}
	
	@CommandLine.Command(name = "depends")
	public int depends(
			@CommandLine.Parameters(index = "0", arity = "0..n", descriptionKey = "mods") ArrayList<String> mods,
			@CommandLine.Mixin ForgeMods.DossiersOptions dossiers,
			@CommandLine.Option(names = {"-a", "--all"}) boolean all, @CommandLine.Mixin ForgeMods.Help help) {
		final DepotLocal depotLocal;
		final DepotInstallation depotInstallation;
		
		try {
			depotLocal = new DepotLocal(dossiers.depot);
			depotLocal.importation();
			depotInstallation = DepotInstallation.depot(dossiers.minecraft);
		} catch (IOException e) {
			System.err.println("Erreur de lecture du dépôt.");
			return 1;
		}
		
		// Liste des versions pour lesquels chercher les dépendances.
		List<PaquetMinecraft> listeRecherche;
		
		if (all) {
			listeRecherche = depotInstallation.getModids().stream().map(depotInstallation::informations)
					.map(ins -> ins.paquet).collect(Collectors.toList());
		} else if (mods != null && mods.size() > 0) {
			final List<PaquetMinecraft> resultat = new ArrayList<>();
			final Map<String, VersionIntervalle> recherche;
			
			try {
				recherche = VersionIntervalle.lectureDependances(mods);
			} catch (IllegalArgumentException iae) {
				System.err.println("[ERROR] " + iae.getMessage());
				return 1;
			}
			for (Map.Entry<String, VersionIntervalle> entry : recherche.entrySet()) {
				String modid = entry.getKey();
				VersionIntervalle version = entry.getValue();
				if (depotLocal.contains(modid)) {
					Optional<PaquetMinecraft> trouvee = depotLocal.getModVersions(modid).stream()
							.filter(modVersion -> version.equals(VersionIntervalle.ouvert()) || version
									.contains(modVersion.version)).max(Comparator.comparing(mv -> mv.version));
					if (trouvee.isPresent()) resultat.add(trouvee.get());
					else {
						System.err.printf("Version inconnue pour '%s': '%s'%n", modid, version);
						return 3;
					}
				} else {
					System.err.printf("Modid inconnu: '%s'%n", modid);
					return 3;
				}
			}
			listeRecherche = resultat;
		} else {
			System.err.println("Nécessite une liste de travail.");
			return 4;
		}
		
		// Liste complète des dépendances nécessaire pour la liste des mods présent.
		final SolveurPaquet solveur = new SolveurPaquet(depotLocal, depotInstallation.mcversion);
		solveur.ajoutVariable("forge", Collections.singleton(depotInstallation.forge));
		listeRecherche.forEach(p -> solveur.domaineVariable(p.modid).reduction(p.version));
		solveur.coherence();
		
		System.out.printf("%d dépendances%n", solveur.variables().size());
		for (String dep : solveur.variables()) {
			System.out.println(dep + " " + solveur.domaineVariable(dep).get(0));
		}
		return 0;
	}
	
	@CommandLine.Command(name = "mark")
	public int mark(@CommandLine.Option(names = {"-m", "--minecraft"}) Path minecraft,
			@CommandLine.Parameters(arity = "1", paramLabel = "action") MarkAction action,
			@CommandLine.Parameters(arity = "1..n", paramLabel = "mods") ArrayList<String> mods) {
		final DepotInstallation depotInstallation;
		
		try {
			depotInstallation = DepotInstallation.depot(minecraft);
		} catch (IOException e) {
			System.err.println("Erreur de lecture du dépôt.");
			return 1;
		}
		
		Map<String, VersionIntervalle> versions = VersionIntervalle.lectureDependances(mods);
		for (String modid : versions.keySet()) {
			if (depotInstallation.contains(modid)) {
				DepotInstallation.Installation mv = depotInstallation.informations(modid);
				if (versions.get(modid).contains(mv.paquet.version)) {
					if (action == MarkAction.manual || action == MarkAction.auto) {
						if (mv.verrou()) {
							System.err.printf("%s est verrouillé%n", mv);
						} else mv.manuel = action == MarkAction.manual;
					} else mv.verrou = action == MarkAction.lock;
				} else {
					System.err.printf("Le mod %s@%s n'est pas installé", modid, versions.get(modid));
				}
			}
		}
		
		try {
			depotInstallation.statusSauvegarde();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	@CommandLine.Command(name = "set")
	public int set(@CommandLine.Option(names = {"--minecraft"}) String mcversion,
			@CommandLine.Option(names = {"--forge"}) String forgeversion,
			@CommandLine.Option(names = {"-m", "--dir"}, description = "dossier de minecraft") Path minecraft) {
		final DepotInstallation depotInstallation;
		
		try {
			depotInstallation = DepotInstallation.depot(minecraft);
		} catch (IOException e) {
			System.err.println("Erreur de lecture du dépôt.");
			return 1;
		}
		
		if (mcversion != null) {
			depotInstallation.mcversion = Version.read(mcversion);
		}
		
		if (forgeversion != null) {
			depotInstallation.forge = Version.read(forgeversion);
		}
		
		try {
			depotInstallation.close();
			return 0;
		} catch (IOException e) {
			System.err.println("Impossible de sauvegarder la configuration de l'installation.");
			return 1;
		}
	}
	
	private enum MarkAction {
		manual,
		auto,
		lock,
		unlock
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
		@CommandLine.Option(names = {"-r", "--repo"}, description = "Dépot local à utiliser")
		public Path depot = null;
		
		/**
		 * Spécifie l'installation minecraft à utiliser. Il peut en exister d'autres de ~/.minecraft.
		 */
		@CommandLine.Option(names = {"-m", "--minecraft"}, description = "Dossier minecraft (~/.minecraft)")
		public Path minecraft = null;
	}
}
