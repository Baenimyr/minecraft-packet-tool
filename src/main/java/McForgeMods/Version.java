package McForgeMods;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Permet de décomposer et comparer les versions entre-elles. Une version valide est composés de chiffres séparés par
 * des virgules. Le format minimum est <i>major.minor</i>. Le format le plus large est <i>major.medium.minor.patch</i>.
 *
 * @see <a href="https://semver.org">Semantic Versioning</a>
 * @since 2020-01-10
 */
public class Version implements Comparable<Version> {
	/**
	 * major, medium, minor, patch
	 */
	private final        int[]        versions = new int[4];
	private final        List<String> release  = new LinkedList<>();
	private final        List<String> build    = new LinkedList<>();
	
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
		System.arraycopy(v.versions, 0, this.versions, 0, 4);
	}
	
	public int size() {
		return 4;
	}
	
	/**
	 * Par construction, tous les champs de sous-version sont remplis par 0. Tous les champs sont utilisés pour
	 * l'égalité et la comparaison de Version, cependant dans le cas d'une intervalle, il est interressant de laisser
	 * une certaine souplesse sur les derniers champs nuls.
	 *
	 * @return l'index de la dernière sous-version non nulle.
	 */
	public int precision() {
		for (int i = size() - 1; i >= 0; i--) {
			if (get(i) != 0) return i;
		}
		return 0;
	}
	
	public int get(int index) {
		return this.versions[index];
	}
	
	/**
	 * À utiliser avec prudence
	 */
	public void set(int index, int valeur) {
		this.versions[index] = valeur;
	}
	
	public List<String> getRelease() {
		return this.release;
	}
	
	public List<String> getBuild() {
		return this.build;
	}
	
	public static Version read(String version) throws IllegalArgumentException {
		VersionBuilder builder = new VersionBuilder(version); //.noRelease().noBuild();
		int lu = builder.read();
		if (lu != version.length())
			throw new IllegalArgumentException(String.format("Version illisible: '%s'", version));
		return builder.version();
	}
	
	@Override
	public int compareTo(Version version) {
		if (this.versions[0] == version.versions[0]) {
			if (this.versions[1] == version.versions[1]) {
				if (this.versions[2] == version.versions[2]) {
					if (this.versions[3] == version.versions[3]) {
						for (int i = 0; i < this.release.size() && i < version.release.size(); i++) {
							int diff = this.release.get(i).compareTo(version.release.get(i));
							if (diff != 0) return diff;
						}
						return Integer.compare(this.release.size(), version.release.size());
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
		for (int i = 1; i < size() && i <= p; i++) {
			sb.append('.');
			sb.append(this.versions[i]);
		}
		if (release && !this.release.isEmpty()) {
			sb.append("-");
			sb.append(String.join(".", this.release));
		}
		if (build && !this.build.isEmpty()) {
			sb.append("+");
			sb.append(String.join(".", this.build));
		}
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		return Arrays.equals(versions, ((Version) o).versions) && Arrays
				.equals(this.release.toArray(), ((Version) o).release.toArray());
	}
	
	@Override
	public int hashCode() {
		return Arrays.hashCode(versions);
	}
	
	
	public static class VersionBuilder {
		private final String texte;
		private       int    lecture = 0;
		
		private boolean accept_release = true;
		private boolean accept_build   = true;
		/**
		 * Nombre de sous-version réellement lues.
		 */
		public  int     precision      = 0;
		
		private       int          major   = 0;
		private       int          medium  = 0;
		private       int          minor   = 0;
		private       int          patch   = 0;
		private final List<String> release = new LinkedList<>();
		private final List<String> build   = new LinkedList<>();
		
		public VersionBuilder(String texte) {
			this.texte = texte;
		}
		
		public VersionBuilder(String texte, int depart) {
			this(texte);
			this.lecture = depart;
		}
		
		public Version version() {
			final Version version = new Version(major, medium, minor, patch);
			version.release.addAll(this.release);
			version.build.addAll(this.build);
			return version;
		}
		
		public VersionBuilder noRelease() {
			this.accept_release = false;
			return this;
		}
		
		public VersionBuilder noBuild() {
			this.accept_build = false;
			return this;
		}
		
		public int read() throws IllegalArgumentException {
			lectureVersion();
			while (lecture < texte.length()) {
				char c = texte.charAt(lecture);
				if (this.accept_release && c == '-') {
					lecture++;
					lectureRelease();
				} else if (this.accept_build && c == '+') {
					lecture++;
					lectureBuild();
				} else break;
			}
			return lecture;
		}
		
		private int lectureChiffre() {
			int pos = lecture;
			while (pos < texte.length() && Character.isDigit(texte.charAt(pos))) pos++;
			if (lecture == pos) throw new IllegalArgumentException(
					String.format("Absence de nombre en position %d dans '%s' !", lecture, this.texte));
			int i = Integer.parseInt(texte.substring(lecture, pos));
			lecture = pos;
			return i;
		}
		
		private void lectureVersion() {
			this.major = lectureChiffre();
			if (lecture < texte.length() && texte.charAt(lecture) == '.') {
				lecture++;
				this.medium = lectureChiffre();
				precision = 1;
				if (lecture < texte.length() && texte.charAt(lecture) == '.') {
					lecture++;
					this.minor = lectureChiffre();
					precision = 2;
					if (lecture < texte.length() && texte.charAt(lecture) == '.') {
						lecture++;
						this.patch = lectureChiffre();
						precision = 3;
					}
				}
			}
		}
		
		private void lectureRelease() {
			int pos = lecture;
			while (pos < texte.length() && (Character.isDigit(texte.charAt(pos)) || Character
					.isAlphabetic(texte.charAt(pos)) || texte.charAt(pos) == '.')) {
				if (texte.charAt(pos) == '.') {
					if (lecture == pos) throw new IllegalArgumentException(
							String.format("Absence de texte en position %d dans '%s'", lecture, this.texte));
					this.release.add(texte.substring(lecture, pos));
					lecture = ++pos;
				} else pos++;
			}
			
			if (lecture == pos) throw new IllegalArgumentException(
					String.format("Absence de texte en position %d dans '%s' !", lecture, this.texte));
			this.release.add(this.texte.substring(lecture, pos));
			lecture = pos;
		}
		
		private void lectureBuild() {
			int pos = lecture;
			while (pos < texte.length() && (Character.isDigit(texte.charAt(pos)) || Character
					.isAlphabetic(texte.charAt(pos)) || texte.charAt(pos) == '.')) {
				if (texte.charAt(pos) == '.') {
					if (lecture == pos) throw new IllegalArgumentException(
							String.format("Absence de texte en position %d dans '%s'", lecture, this.texte));
					this.build.add(texte.substring(lecture, pos));
					lecture = ++pos;
				} else pos++;
			}
			
			if (lecture == pos) throw new IllegalArgumentException(
					String.format("Absence de texte en position %d dans '%s' !", lecture, this.texte));
			this.build.add(this.texte.substring(lecture, pos));
			lecture = pos;
		}
	}
}
