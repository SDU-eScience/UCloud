package ucx

import (
	"context"
	"fmt"
	"net/http"
	"sync"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// NOTE(Dan): This interface is primarily intended for application developers and not really intended for internal
// UCloud development. For example, this interface does not put any real emphasis on the authentication since this is
// already handled by the outer layers.

type Application interface {
	Mutex() *sync.Mutex
	Session() **Session
	UserInterface() UiNode

	OnInit()
	OnMessage(message Frame)
}

type SysHelloAwareApplication interface {
	OnSysHello(payload string)
}

// AppUpdateModel serializes the whole application and sends the changed fields. Expects the caller to
// hold the application mutex. All event handlers (OnInit, OnInput, OnMessage and UI handlers) will
// automatically acquire the mutex before running the handler. Manually acquiring the mutex is only
// needed if running a background task.
//
// Background tasks that perform network IO must not hold the mutex across the IO. Use AppSnapshot to
// serialize while holding the mutex, capture the session, then AppUpdateModelLocked to send after
// releasing the mutex. Holding it across the send can freeze the UI when the transport stalls.
func AppUpdateModel(app Application) {
	session := *app.Session()
	AppUpdateModelLocked(session, AppSnapshot(app))
}

// AppSnapshot serializes the whole application. Expects the caller to hold the application mutex.
func AppSnapshot(app Application) map[string]Value {
	model, err := ValueMarshal(app)
	if err != nil {
		log.Warn("Failed to serialize model %#v: %s", app, err)
		return nil
	}
	return model
}

func AppUpdateModelLocked(session *Session, model map[string]Value) {
	if model == nil {
		return
	}
	session.SendModel(model)
}

func AppUpdateModelPatch(session *Session, changes map[string]Value) {
	session.SendModelPatch(changes)
}

func AppUpdateUi(app Application) {
	// Expects caller to hold mutex. All event handlers (OnInit, OnInput, OnMessage and UI handlers) will
	// automatically acquire the mutex before running the handler. Manually acquiring the mutex is only needed if
	// running a background task.

	session := *app.Session()
	AppUpdateUiLocked(session, app.UserInterface(), AppSnapshot(app))
}

func AppUpdateUiLocked(session *Session, ui UiNode, model map[string]Value) {
	if model == nil {
		return
	}
	session.SendUiMount(UiMount{
		InterfaceId: "-",
		Root:        ui,
		Model:       model,
	})
}

func AppServe(factory func() Application, port util.Option[int]) {
	upstreamServer := &rpc.Server{
		Mux: http.NewServeMux(),
	}

	streamCall := rpc.Call[util.Empty, util.Empty]{
		BaseContext: "/",
		Convention:  rpc.ConventionWebSocket,
		Roles:       rpc.RolesPublic,
	}

	streamCall.HandlerEx(upstreamServer, func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		conn := info.WebSocket

		defer util.SilentClose(conn)
		ctx, cancel := context.WithCancel(context.Background())
		defer cancel()

		app := factory()

		stateMu := app.Mutex()

		RunAppWebSocket(
			conn,
			ctx,
			func(ctx context.Context, token string) bool {
				return true
			},
			func(ctx context.Context, session *Session) {
				stateMu.Lock()
				sessionPtr := app.Session()
				*sessionPtr = session
				session.app = app
				app.OnInit()
				stateMu.Unlock()

				for {
					select {
					case <-ctx.Done():
						return
					case frame, ok := <-session.Incoming():
						if !ok {
							return
						}

						if frame.Opcode == OpSysHello {
							stateMu.Lock()
							if appWithSysHello, ok := app.(SysHelloAwareApplication); ok {
								appWithSysHello.OnSysHello(frame.SysHello.Payload)
							}
							ui := app.UserInterface()
							model, err := ValueMarshal(app)
							if err != nil {
								log.Warn("Failed to serialize model %#v: %s", app, err)
							} else {
								session.SendUiMount(UiMount{
									InterfaceId: "-",
									Root:        ui,
									Model:       model,
								})
							}
							stateMu.Unlock()
						} else if frame.Opcode == OpUiEvent {
							(func() {
								stateMu.Lock()
								defer stateMu.Unlock()

								if session.DispatchUiEvent(frame.UiEvent) {
									AppUpdateModel(app)
								} else {
									app.OnMessage(frame)
								}
							})()
						} else if frame.Opcode == OpModelInput {
							(func() {
								stateMu.Lock()
								defer stateMu.Unlock()

								if err := ApplyModelInput(app, frame.ModelInput); err != nil {
									return
								}

								app.OnMessage(frame)
								AppUpdateModel(app)
							})()
						}
					}
				}
			},
		)
		return util.Empty{}, nil
	})

	s := &http.Server{
		Addr:    fmt.Sprintf(":%v", port.GetOrDefault(8080)),
		Handler: upstreamServer.Mux,
	}
	_ = s.ListenAndServe()
}
