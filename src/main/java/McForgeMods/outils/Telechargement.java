package McForgeMods.outils;

import McForgeMods.ModVersion;

import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public abstract class Telechargement implements Callable<Integer> {
	protected static final int BUFFER = 2048;
	
	public final    ModVersion mod;
	public final    URL        url;
	protected final Path       dossier_cible;
	public          long       telecharge  = 0;
	public          long       tailleTotal = 0;
	
	public final FutureTask<Integer> future;
	
	protected Telechargement(ModVersion mod, URL url, Path minecraft) {
		this.url = url;
		this.mod = mod;
		this.dossier_cible = Dossiers.dossierInstallationMod(minecraft, mod);
		this.future = new FutureTask<>(this);
	}
	
	protected boolean creationDossier() {
		return this.dossier_cible.toFile().exists() || this.dossier_cible.toFile().mkdirs();
	}
	
	protected void changeTailleTotale(long taille) {
		this.tailleTotal = taille;
	}
	
	protected void ajoutTelecharge(long telecharge) {
		this.telecharge += telecharge;
	}
	
	protected long reste() {
		return this.tailleTotal - this.telecharge;
	}
	
	public boolean succes() {
		return this.tailleTotal > 0 && this.telecharge == this.tailleTotal;
	}
}
