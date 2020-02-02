package tar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class EntreeTar {
	public final  String nom;
	public final  String mode;
	public final  int    taille;
	public final  long   date;
	private final byte[] contenu;
	
	EntreeTar(final byte[] teteb, final InputStream stream) throws IOException {
		int pos = 0;
		while (teteb[pos] != 0 && pos < 100) {
			pos++;
		}
		
		this.nom = new String(teteb, 0, pos, StandardCharsets.US_ASCII);
		this.mode = new String(teteb, 100, 8 - 1, StandardCharsets.US_ASCII);
		this.taille = Integer.parseInt(new String(teteb, 124, 12 - 1, StandardCharsets.US_ASCII), 8);
		this.date = Long.parseLong(new String(teteb, 136, 12 - 1, StandardCharsets.US_ASCII), 8);
		this.contenu = new byte[this.taille];
		
		int lu = 0;
		while (lu < this.taille) lu += stream.read(this.contenu, lu, this.taille - lu);
	}
	
	public InputStream getInputStream() {
		return new ByteArrayInputStream(this.contenu);
	}
	
	public boolean dossier() {
		return this.taille == 0 && this.nom.endsWith("/");
	}
	
	public int size() {
		return this.taille;
	}
	
	@Override
	public String toString() {
		return "EntreeTar{" + "nom='" + nom + '\'' + ", mode='" + mode + '\'' + ", taille=" + taille + ", date=" + date
				+ "}";
	}
}
