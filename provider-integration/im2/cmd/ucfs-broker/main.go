package main

import (
	ucfsbroker "ucloud.dk/pkg/integrations/k8s/ucfs-broker"
)

func main() {
	/*
		if os.Getenv("UCFS_BROKER_CHILD") == "" {
			cmd := exec.Command(os.Args[0], os.Args[1:]...)
			cmd.Env = append(os.Environ(), "UCFS_BROKER_CHILD=1")
			cmd.Stdout = os.Stdout
			cmd.Stderr = os.Stderr
			cmd.Stdin = nil

			if err := cmd.Start(); err != nil {
				log.Fatalf("spawn child: %v", err)
			}
			for {
				time.Sleep(1 * time.Second)
			}
		}
	*/

	ucfsbroker.Launch()
}
