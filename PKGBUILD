pkgname="minepac"
pkgver=0.4.2
pkgrel=1
pkgdesc="Gestionnaire de paquet pour minecraft"
arch=('any')
depends=('java-environment>=11')
source=(
${pkgname}-${pkgver}.tar
)
sha256sums=('SKIP')

package() {
	install -Dm755 minepac-${pkgver}/bin/minepac -t ${pkgdir}/opt/${pkgname}/bin
	install -Dm644 minepac-${pkgver}/lib/* -t ${pkgdir}/opt/${pkgname}/lib
	install -dm755 ${pkgdir}/usr/bin
	ln -s /opt/${pkgname}/bin/minepac -t ${pkgdir}/usr/bin
}