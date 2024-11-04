package k8s

import (
	"context"
	"fmt"
	core "k8s.io/api/core/v1"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/clientcmd"
	"math"
	"os"
	"time"
	"ucloud.dk/pkg/apm"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var machineSupport []orc.JobSupport
var K8sClient *kubernetes.Clientset

func InitCompute() ctrl.JobsService {
	loadComputeProducts()
	createKubernetesClient()

	go func() {
		if len(Machines) == 0 {
			return
		}

		for util.IsAlive {
			loopComputeMonitoring()
			loopAccounting()
			time.Sleep(5 * time.Second)
		}
	}()

	return ctrl.JobsService{
		Submit:                   nil,
		Terminate:                nil,
		Extend:                   nil,
		RetrieveProducts:         nil,
		Follow:                   nil,
		HandleShell:              nil,
		ServerFindIngress:        nil,
		OpenWebSession:           nil,
		RequestDynamicParameters: nil,
	}
}

func createKubernetesClient() {
	composeFile := "/mnt/k3s/kubeconfig.yaml"
	_, err := os.Stat(composeFile)

	var k8sClient *kubernetes.Clientset = nil

	if err == nil {
		k8Config, err := clientcmd.BuildConfigFromFlags("", composeFile)
		if err == nil {
			c, err := kubernetes.NewForConfig(k8Config)
			if err == nil {
				k8sClient = c
			}
		}
	}

	if k8sClient == nil {
		k8Config, err := rest.InClusterConfig()
		if err == nil {
			c, err := kubernetes.NewForConfig(k8Config)
			if err == nil {
				k8sClient = c
			}
		}
	}

	if k8sClient == nil {
		log.Error("Could not connect to Kubernetes through any of the known configuration methods")
		os.Exit(1)
		return
	}

	K8sClient = k8sClient
}

func pickResource(resource cfg.MachineResourceType, machineConfig cfg.K8sMachineConfiguration) int {
	switch resource {
	case cfg.MachineResourceTypeCpu:
		return machineConfig.Cpu
	case cfg.MachineResourceTypeGpu:
		return machineConfig.Gpu
	case cfg.MachineResourceTypeMemory:
		return machineConfig.MemoryInGigabytes
	default:
		log.Warn("Unhandled machine resource type: %v", resource)
		return 0
	}
}

func loadComputeProducts() {
	for categoryName, category := range ServiceConfig.Compute.Machines {
		productCategory := apm.ProductCategory{
			Name:                categoryName,
			Provider:            cfg.Provider.Id,
			ProductType:         apm.ProductTypeCompute,
			AccountingFrequency: apm.AccountingFrequencyPeriodicMinute,
			FreeToUse:           false,
			AllowSubAllocations: true,
		}

		usePrice := false
		switch category.Payment.Type {
		case cfg.PaymentTypeMoney:
			productCategory.AccountingUnit.Name = category.Payment.Currency
			productCategory.AccountingUnit.NamePlural = category.Payment.Currency
			productCategory.AccountingUnit.DisplayFrequencySuffix = false
			productCategory.AccountingUnit.FloatingPoint = true
			usePrice = true

		case cfg.PaymentTypeResource:
			switch category.Payment.Unit {
			case cfg.MachineResourceTypeCpu:
				productCategory.AccountingUnit.Name = "Core"
			case cfg.MachineResourceTypeGpu:
				productCategory.AccountingUnit.Name = "GPU"
			case cfg.MachineResourceTypeMemory:
				productCategory.AccountingUnit.Name = "GB"
			}

			productCategory.AccountingUnit.NamePlural = productCategory.AccountingUnit.Name
			productCategory.AccountingUnit.FloatingPoint = false
			productCategory.AccountingUnit.DisplayFrequencySuffix = true
		}

		switch category.Payment.Interval {
		case cfg.PaymentIntervalMinutely:
			productCategory.AccountingFrequency = apm.AccountingFrequencyPeriodicMinute
		case cfg.PaymentIntervalHourly:
			productCategory.AccountingFrequency = apm.AccountingFrequencyPeriodicHour
		case cfg.PaymentIntervalDaily:
			productCategory.AccountingFrequency = apm.AccountingFrequencyPeriodicDay
		}

		for groupName, group := range category.Groups {
			for _, machineConfig := range group.Configs {
				name := fmt.Sprintf("%v-%v", groupName, pickResource(group.NameSuffix, machineConfig))
				product := apm.ProductV2{
					Type:        apm.ProductTypeCCompute,
					Category:    productCategory,
					Name:        name,
					Description: "A compute product", // TODO

					ProductType:               apm.ProductTypeCompute,
					Price:                     int64(math.Floor(machineConfig.Price * 1_000_000)),
					HiddenInGrantApplications: false,

					Cpu:          machineConfig.Cpu,
					CpuModel:     group.CpuModel,
					MemoryInGigs: machineConfig.MemoryInGigabytes,
					MemoryModel:  group.MemoryModel,
					Gpu:          machineConfig.Gpu,
					GpuModel:     group.GpuModel,
				}

				if !usePrice {
					product.Price = int64(pickResource(category.Payment.Unit, machineConfig))
				}

				Machines = append(Machines, product)
			}
		}
	}

	for _, machine := range Machines {
		support := orc.JobSupport{
			Product: apm.ProductReference{
				Id:       machine.Name,
				Category: machine.Category.Name,
				Provider: cfg.Provider.Id,
			},
		}

		support.Native.Enabled = true
		support.Native.Web = true
		support.Native.Vnc = true
		support.Native.Logs = true
		support.Native.Terminal = true
		support.Native.Peers = false
		support.Native.TimeExtension = false

		machineSupport = append(machineSupport, support)
	}
}

func loopComputeMonitoring() {}
func loopAccounting()        {}

func findPodByJobIdAndRank(jobId string, rank int) util.Option[*core.Pod] {
	result, err := K8sClient.CoreV1().Pods(ServiceConfig.Compute.Namespace).Get(
		context.TODO(),
		idAndRankToPodName(jobId, rank),
		meta.GetOptions{},
	)

	if err != nil {
		return util.Option[*core.Pod]{}
	} else {
		return util.OptValue(result)
	}
}
