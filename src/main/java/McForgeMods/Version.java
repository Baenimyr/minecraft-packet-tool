package McForgeMods;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Permet de décomposer et comparer les versions entre-elles. Une version valide est composés de chiffres séparés par
 * des virgules. Le format minimum est <i>major.minor</i>. Le format le plus large est <i>major.medium.minor.patch</i>.
 *
 * @see <a href="https://semver.org">Semantic Versioning</a>
 * @since 2020-01-10
 */
public class Version implements Comparable<Version> {
	public static final int     size     = 4;
	public static final Pattern VERSION  = Pattern.compile(
			"(\\d+)(\\.(\\d+)(\\.(\\d+)(\\.(\\d+))?)?)?(-(?<release>[\\p{Alnum}.]+))?(\\+(?<build>[\\p{Alnum}.]+))?");
	/**
	 * major, medium, minor, patch
	 */
	private final       int[]   versions = new int[size];
	public              String  release  = null;
	public              String  build    = null;
	
	public Version(int major, int medium, int minor, int patch) {
		this.versions[0] = major;
		this.versions[1] = medium;
		this.versions[2] = minor;
		this.versions[3] = patch;
	}
	
	public Version(int major, int medium, int minor) {
		this(major, medium, minor, 0);
	}
	
	public Version(Version v) {
		System.arraycopy(v.versions, 0, this.versions, 0, size);
		this.release = v.release;
		this.build = v.build;
	}
	
	/**
	 * Par construction, tous les champs de sous-version sont remplis par 0. Tous les champs sont utilisés pour
	 * l'égalité et la comparaison de Version, cependant dans le cas d'une intervalle, il est interressant de laisser
	 * une certaine souplesse sur les derniers champs nuls.
	 *
	 * @return l'index de la dernière sous-version non nulle.
	 */
	public int precision() {
		for (int i = size - 1; i >= 0; i--) {
			if (get(i) != 0) return i;
		}
		return 0;
	}
	
	public int get(int index) {
		return this.versions[index];
	}
	
	public Version set(int index, int valeur) {
		Version v = new Version(this);
		v.versions[index] = valeur;
		return v;
	}
	
	public static Version read(String texte) throws IllegalArgumentException {
		Matcher m = VERSION.matcher(texte);
		if (m.find() && m.hitEnd()) {
			return read(m);
		} else throw new IllegalArgumentException(String.format("Version illisible: '%s'", texte));
	}
	
	public static Version read(Matcher m) {
		Version version;
		String medium = m.group(3);
		String minor = m.group(5);
		String patch = m.group(7);
		String release = m.group("release");
		String build = m.group("build");
		
		int v_medium = medium == null ? 0 : Integer.parseInt(medium);
		int v_minor = minor == null ? 0 : Integer.parseInt(minor);
		
		if (patch != null)
			version = new Version(Integer.parseInt(m.group(1)), v_medium, v_minor, Integer.parseInt(patch));
		else version = new Version(Integer.parseInt(m.group(1)), v_medium, v_minor);
		
		if (release != null) version.release = release;
		if (build != null) version.build = build;
		return version;
	}
	
	public String getRelease() {
		return this.release;
	}
	
	public String getBuild() {
		return this.build;
	}
	
	@Override
	public int compareTo(Version version) {
		if (this.versions[0] == version.versions[0]) {
			if (this.versions[1] == version.versions[1]) {
				if (this.versions[2] == version.versions[2]) {
					if (this.versions[3] == version.versions[3]) {
						return this.release == null ? (version.release == null ? 0 : -1) : version.release == null ? 1
								: Objects.compare(this.release, version.release, String::compareTo);
					} else return Integer.compare(this.versions[3], version.versions[3]);
				} else return Integer.compare(this.versions[2], version.versions[2]);
			} else return Integer.compare(this.versions[1], version.versions[1]);
		} else return Integer.compare(this.versions[0], version.versions[0]);
	}
	
	@Override
	public String toString() {
		return this.toString(this.versions[3] != 0 ? 3 : 2, true, true);
	}
	
	public String toString(int p, boolean release, boolean build) {
		StringBuilder sb = new StringBuilder();
		sb.append(this.versions[0]);
		for (int i = 1; i < size && i <= p; i++) {
			sb.append('.');
			sb.append(this.versions[i]);
		}
		if (release && this.release != null) {
			sb.append("-");
			sb.append(String.join(".", this.release));
		}
		if (build && this.build != null) {
			sb.append("+");
			sb.append(String.join(".", this.build));
		}
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		return Arrays.equals(versions, ((Version) o).versions) && Objects.equals(this.release, ((Version) o).release);
	}
	
	@Override
	public int hashCode() {
		return Arrays.hashCode(versions);
	}
}
