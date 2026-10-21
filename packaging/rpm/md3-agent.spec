# Built by packaging/rpm/build-rpm.sh from dist/md3-agent (the prebuilt runtime and jar); nothing is compiled here.
Name:           md3-agent
Version:        %{version}
Release:        1%{?dist}
Summary:        AI assistant with tools, MCP and long-term memory
License:        GPL-3.0-only
URL:            https://github.com/lowsense-dev/MD3-Agent-Desktop
BuildArch:      x86_64
AutoReqProv:    no
Requires:       libX11, libXext, libXrender, libXtst, libXi, freetype, fontconfig, alsa-lib
Recommends:     bubblewrap, python3, wl-clipboard, xclip, xdg-desktop-portal
Suggests:       speech-dispatcher

%description
MD3 Agent Desktop: chat with any OpenAI-compatible, Anthropic or Gemini model, with web search, files, shell and
Python tools, reminders, MCP servers and long-term memory, in a Material 3 Expressive interface.

%install
mkdir -p %{buildroot}/opt %{buildroot}%{_bindir} %{buildroot}%{_datadir}
cp -a %{dist_dir} %{buildroot}/opt/md3-agent
mv %{buildroot}/opt/md3-agent/share/applications %{buildroot}%{_datadir}/
mv %{buildroot}/opt/md3-agent/share/icons %{buildroot}%{_datadir}/
rmdir %{buildroot}/opt/md3-agent/share
ln -s /opt/md3-agent/bin/md3-agent %{buildroot}%{_bindir}/md3-agent

%files
%license /opt/md3-agent/LICENSE
/opt/md3-agent
%{_bindir}/md3-agent
%{_datadir}/applications/md3-agent.desktop
%{_datadir}/icons/hicolor/*/apps/md3-agent.png
