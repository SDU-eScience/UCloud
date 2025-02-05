%define version %(cat ../../../backend/version.txt)

Name: ucloud-im
Version: %{version}
Release: 1%{?dist}
Summary: UCloud Integration Module
License: EUPL-1.2
URL: https://github.com/SDU-eScience/UCloud

BuildRequires: git
Requires(pre): shadow-utils
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd

Source0: https://raw.githubusercontent.com/SDU-eScience/UCloud/refs/heads/master/LICENSE.md
Source1: sudoers
Source2: %{name}.service
Source3: %{name}.default
Source4: %{name}.preset

%define debug_package %{nil}
%define appdir /opt/ucloud
%define etcdir /etc/ucloud

%description
UCloud/IM is the middleware needed to integrated with the UCloud platform

%prep

%build
cd ../../../../provider-integration/im2
./build.sh

%install
install -d -m 755 %{buildroot}%{appdir}
install -d -m 755 %{buildroot}%{etcdir}
install -d -m 750 %{buildroot}%{_sysconfdir}/sudoers.d
install -D -m 755 %{_builddir}/../../../../provider-integration/im2/bin/im %{buildroot}%{appdir}/ucloud
install -D -m 640 %{SOURCE1} %{buildroot}%{_sysconfdir}/sudoers.d/ucloud
install -D -m 644 %{SOURCE0} %{buildroot}%{appdir}/LICENSE
install -D -m 644 %{SOURCE2} %{buildroot}%{_unitdir}/%{name}.service
install -D -m 644 %{SOURCE3} %{buildroot}%{_sysconfdir}/default/%{name}
install -D -m 644 %{SOURCE4} %{buildroot}%{_presetdir}/50-%{name}.preset

%clean

%files
%defattr(-,root,root,-)
%{_unitdir}/%{name}.service
%{_presetdir}/50-%{name}.preset
%config(noreplace) %{_sysconfdir}/default/%{name}
%config(noreplace) %{_sysconfdir}/sudoers.d/ucloud
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

%preun
%systemd_preun %{name}.service

%postun
%systemd_postun_with_restart %{name}.service
