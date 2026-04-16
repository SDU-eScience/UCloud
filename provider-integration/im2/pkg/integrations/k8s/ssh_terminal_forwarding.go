package k8s

import (
	"fmt"
	"io"
	"net"
	"strconv"
	"time"

	"github.com/charmbracelet/ssh"
	gossh "golang.org/x/crypto/ssh"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const sshTerminalContextOwnerKey = "owner"
const sshTerminalContextJobIDKey = "ucloud-k8s-integrated-terminal-job-id"

func configureSshTerminalForwarding(server *ssh.Server) {
	if server.ChannelHandlers == nil {
		server.ChannelHandlers = map[string]ssh.ChannelHandler{}
	}
	if _, ok := server.ChannelHandlers["session"]; !ok {
		server.ChannelHandlers["session"] = ssh.DefaultSessionHandler
	}
	server.ChannelHandlers["direct-tcpip"] = sshTerminalDirectTCPIPHandler

	if server.RequestHandlers == nil {
		server.RequestHandlers = map[string]ssh.RequestHandler{}
	}
	forwardHandler := &ssh.ForwardedTCPHandler{}
	server.RequestHandlers["tcpip-forward"] = forwardHandler.HandleSSHRequest
	server.RequestHandlers["cancel-tcpip-forward"] = forwardHandler.HandleSSHRequest

	allowForwarding := func(ctx ssh.Context, _ string, _ uint32) bool {
		_, ok := sshTerminalForwardJob(ctx)
		return ok
	}
	server.LocalPortForwardingCallback = ssh.LocalPortForwardingCallback(allowForwarding)
	server.ReversePortForwardingCallback = ssh.ReversePortForwardingCallback(allowForwarding)
}

func sshTerminalDirectTCPIPHandler(srv *ssh.Server, conn *gossh.ServerConn, newChan gossh.NewChannel, ctx ssh.Context) {
	// We intentionally ignore the client-supplied destination host and only dial the sandbox job host.
	_ = srv
	_ = conn

	type directTCPIPRequest struct {
		DestAddr   string
		DestPort   uint32
		OriginAddr string
		OriginPort uint32
	}

	var req directTCPIPRequest
	if err := gossh.Unmarshal(newChan.ExtraData(), &req); err != nil {
		_ = newChan.Reject(gossh.ConnectionFailed, "error parsing forward data: "+err.Error())
		return
	}

	job, ok := sshTerminalForwardJob(ctx)
	if !ok {
		_ = newChan.Reject(gossh.Prohibited, "port forwarding is disabled")
		return
	}
	if req.DestPort == 0 {
		_ = newChan.Reject(gossh.ConnectionFailed, "invalid destination port")
		return
	}

	destHost := shared.JobHostName(job, 0)
	if util.DevelopmentModeEnabled() {
		podName := sshTerminalPodName(job.Id, 0)
		tunnelPort := shared.EstablishTunnelEx(podName, shared.ServiceConfig.Compute.Namespace, int(req.DestPort))
		destHost = "127.0.0.1"
		req.DestPort = uint32(tunnelPort)
	}

	dconn, err := sshTerminalDialSandboxPort(ctx, destHost, int(req.DestPort))
	if err != nil {
		_ = newChan.Reject(gossh.ConnectionFailed, err.Error())
		return
	}

	ch, reqs, err := newChan.Accept()
	if err != nil {
		_ = dconn.Close()
		return
	}
	go gossh.DiscardRequests(reqs)

	go func() {
		defer util.SilentClose(ch)
		defer util.SilentClose(dconn)
		_, _ = io.Copy(ch, dconn)
	}()
	go func() {
		defer util.SilentClose(ch)
		defer util.SilentClose(dconn)
		_, _ = io.Copy(dconn, ch)
	}()
}

func sshTerminalForwardJob(ctx ssh.Context) (*orc.Job, bool) {
	if jobID, ok := ctx.Value(sshTerminalContextJobIDKey).(string); ok && jobID != "" {
		if job, ok := controller.JobRetrieve(jobID); ok {
			return job, true
		}
	}

	ownerName, ok := ctx.Value(sshTerminalContextOwnerKey).(string)
	if !ok || ownerName == "" {
		return nil, false
	}

	owner := orc.ResourceOwner{CreatedBy: ownerName}
	config := controller.IAppRetrieveConfiguration(shared.IntegratedTerminalAppName, owner)
	if !config.Present || config.Value.JobId == "" {
		return nil, false
	}

	job, ok := controller.JobRetrieve(config.Value.JobId)
	if !ok {
		return nil, false
	}

	ctx.SetValue(sshTerminalContextJobIDKey, job.Id)
	return job, true
}

func sshTerminalPodName(jobID string, rank int) string {
	return fmt.Sprintf("j-%v-job-%v", jobID, rank)
}

func sshTerminalDialSandboxPort(ctx ssh.Context, host string, port int) (net.Conn, error) {
	dest := net.JoinHostPort(host, strconv.Itoa(port))
	var lastErr error
	var dialer net.Dialer
	for attempt := 0; attempt < 20; attempt++ {
		conn, err := dialer.DialContext(ctx, "tcp", dest)
		if err == nil {
			return conn, nil
		}
		lastErr = err
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case <-time.After(100 * time.Millisecond):
		}
	}
	return nil, lastErr
}
