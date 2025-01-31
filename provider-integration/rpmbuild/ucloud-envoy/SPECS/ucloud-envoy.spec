Name: ucloud-envoy
Version: 1.23.12
Release: 1%{?dist}
Summary: Cloud-native high-performance service proxy
License: Apache-2.0
URL: https://www.envoyproxy.io

Requires(pre): shadow-utils
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd
AutoReq: no

Source0: https://github.com/envoyproxy/envoy/releases/download/v%{version}/envoy-%{version}-linux-x86_64
Source1: https://raw.githubusercontent.com/envoyproxy/envoy/refs/heads/main/LICENSE
Source2: %{name}.service
Source3: %{name}.default
Source4: %{name}.preset
Source5: %{name}.yaml

%define debug_package %{nil}
%define appdir /opt/ucloud/envoy
%define etcdir /etc/ucloud/envoy

%description
Envoy is an open source edge and service proxy, designed for cloud-native applications

%prep

%build

%install
install -d -m 755 %{buildroot}%{appdir}
install -d -m 750 %{buildroot}%{etcdir}
install -D -m 755 %{SOURCE0} %{buildroot}%{appdir}/envoy
install -D -m 644 %{SOURCE1} %{buildroot}%{appdir}/LICENSE
install -D -m 644 %{SOURCE2} %{buildroot}%{_unitdir}/%{name}.service
install -D -m 644 %{SOURCE3} %{buildroot}%{_sysconfdir}/default/%{name}
install -D -m 644 %{SOURCE4} %{buildroot}%{_presetdir}/50-%{name}.preset
install -D -m 644 %{SOURCE5} %{buildroot}%{etcdir}/config.yaml

%clean
rm -rf %{buildroot}
rm -rf %{_builddir}

%files
%defattr(-,root,root,-)
%{_unitdir}/%{name}.service
%{_presetdir}/50-%{name}.preset
%config(noreplace) %{_sysconfdir}/default/%{name}
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
