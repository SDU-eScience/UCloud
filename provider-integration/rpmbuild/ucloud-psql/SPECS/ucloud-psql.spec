Name: ucloud-psql
Version: 16.6.0
Release: 1%{?dist}
Summary: Embedded PostgreSQL
License: Apache-2.0
URL: https://github.com/zonkyio/embedded-postgres-binaries

Requires(pre): shadow-utils util-linux
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd
AutoReq: no

Source0: https://repo1.maven.org/maven2/io/zonky/test/postgres/embedded-postgres-binaries-linux-amd64/%{version}/embedded-postgres-binaries-linux-amd64-%{version}.jar
Source1: https://www.apache.org/licenses/LICENSE-2.0.txt
Source2: %{name}.service
Source3: %{name}.default
Source4: %{name}.preset

%define debug_package %{nil}
%define appdir /opt/ucloud/psql
%define etcdir /etc/ucloud/psql

%description
Lightweight bundle of PostgreSQL binaries

%prep

%build
unzip %{SOURCE0}
tar xf *.txz

%install
install -d -m 755 %{buildroot}%{appdir}
install -d -m 750 %{buildroot}%{etcdir}
cp -ar bin %{buildroot}%{appdir}
cp -ar share %{buildroot}%{appdir}
cp -ar lib %{buildroot}%{appdir}
install -D -m 644 %{SOURCE1} %{buildroot}%{appdir}/LICENSE
install -D -m 644 %{SOURCE2} %{buildroot}%{_unitdir}/%{name}.service
install -D -m 644 %{SOURCE3} %{buildroot}%{_sysconfdir}/default/%{name}
install -D -m 644 %{SOURCE4} %{buildroot}%{_presetdir}/50-%{name}.preset

%clean
rm -rf %{buildroot}
rm -rf %{_builddir}

%files
%defattr(-,root,root,-)
%{_unitdir}/%{name}.service
%{_presetdir}/50-%{name}.preset
%config(noreplace) %{_sysconfdir}/default/%{name}
%config(noreplace) %{etcdir}
/opt/ucloud
%attr(-,ucloud,ucloud) /etc/ucloud

%pre
getent group ucloud > /dev/null || groupadd -r ucloud
getent passwd ucloud > /dev/null || \
    useradd -r -d /etc/ucloud -g ucloud \
    -s /sbin/nologin -c "UCloud service account" ucloud
exit 0

%post
%systemd_post %{name}.service
if [ ! -d '%{etcdir}/data' ]; then
    uuidgen > %{etcdir}/password.txt
    chown ucloud:ucloud %{etcdir}/password.txt
    chmod 0600 %{etcdir}/password.txt
    su ucloud -c '%{appdir}/bin/initdb -D %{etcdir}/data -A md5 --pwfile=%{etcdir}/password.txt' > /dev/null
fi

%preun
%systemd_preun %{name}.service

%postun
%systemd_postun_with_restart %{name}.service
