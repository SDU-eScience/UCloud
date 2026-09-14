Here is a quick tour of the markdown features that a streaming response can contain. The cluster overview below includes headings, lists, code blocks, math, and tables.

# Cluster overview

The table below lists the compute nodes along with their primary workload and current utilization.

| Node | CPU cores | Memory | Primary workload | Utilization |
|------|-----------|--------|------------------|-------------|
| node-01 | 64 | 256 GiB | Batch jobs | 78% |
| node-02 | 128 | 512 GiB | Virtual machines | 54% |
| node-03 | 32 | 128 GiB | Interactive sessions | 91% |
| node-04 | 96 | 384 GiB | Batch jobs | 33% |
| node-05 | 64 | 256 GiB | Databases | 66% |
| node-06 | 128 | 512 GiB | Machine learning training | 87% |
| node-07 | 32 | 64 GiB | Development and testing | 42% |
| node-08 | 96 | 384 GiB | Web services | 59% |

## Utilization thresholds

The averages from the last hour are evaluated against three thresholds:

- **Healthy** between 40% and 85%
- **Underused** below 40%, can host additional workloads
- **Overloaded** above 85%, becomes a load balancing candidate
  - Rebalanced at most once every 15 minutes
  - At most two workloads migrate per cycle

### Escalation steps

1. Verify the workload mix on the node
2. Migrate the two least critical workloads
3. Re-evaluate after 30 minutes
4. Notify an operator if the node is still overloaded

## Inspecting nodes from the CLI

The `nodes` command group reports the same numbers:

```bash
ucloud compute nodes list --sort utilization
ucloud compute nodes describe node-03 --utilization
```

A short script can flag the overloaded nodes:

```python
from ucloud import compute

for node in compute.nodes.list():
    if node.utilization > 0.85:
        print(node.name, "overloaded")
```

> The CLI rounds utilization to whole percentages. The API returns the raw float
> values, so small differences between the two are expected.

## Sizing guidance

Memory headroom is `total - reserved`, for example 256 GiB with 32 GiB reserved
leaves 224 GiB. The guidance in [the sizing handbook](https://example.com/docs/sizing)
recommends keeping at least *ten percent* of the memory unreserved for bursts.

When a node is picked uniformly at random, the probability of seeing an overloaded
node is $p \approx 0.11$. For a sample of $n$ nodes the probability of at least one
overloaded node follows the binomial distribution:

$$
P(\text{at least one}) = 1 - \sum_{k=0}^{n-1} \binom{n}{k} p^k (1-p)^{n-k}
$$

---

## Capacity summary

| Pool | Nodes | Total cores | Total memory | Lipsum |
|------|-------|-------------|--------------|--------|
| General | 5 | 352 | 1.25 TiB | N/A |
| Compute optimized | 2 | 224 | 768 GiB | Vero at praesentium eos placeat et ut distinctio dolor. A saepe a libero sit et. Ad non cumque nobis corrupti doloremque error. Debitis qui architecto tempora ipsam. Quibusdam temporibus aut nemo culpa dicta. Est reprehenderit ipsa enim labore. Rem eos modi similique ratione. Ut facere iure suscipit est aliquam rem. Quaerat doloribus iusto et. |
| Memory optimized | 1 | 64 | 256 GiB | N / A |

Overall the cluster runs at a healthy average utilization with no immediate capacity concerns.

## Lorem ipsum

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Fusce dictum fermentum nulla sit amet dignissim. Pellentesque in tempus erat. Quisque vestibulum luctus mauris, sit amet volutpat urna ultricies eu. Curabitur ac efficitur arcu. Aliquam rutrum nulla mi. Duis ultrices fermentum venenatis. Mauris in enim in sapien aliquet cursus. Donec ac lorem id ante ultricies mattis nec eget tellus.

Orci varius natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Fusce ac mi placerat, vulputate nunc sit amet, tempus dui. Proin facilisis eleifend purus non viverra. Proin at neque purus. Curabitur nec est eget nunc ornare suscipit a at metus. Fusce nec dui sollicitudin, volutpat ante et, mollis purus. Sed sagittis dui tortor, quis malesuada elit imperdiet nec. Duis justo est, ultrices nec ultrices vulputate, faucibus sit amet ipsum. Mauris aliquet pharetra mauris a egestas. Vestibulum non nibh luctus felis commodo ullamcorper. Donec dapibus id nunc a tempus.

Aliquam tempor scelerisque felis sit amet laoreet. Morbi non velit libero. In a metus enim. Vestibulum facilisis consequat lorem, vitae eleifend elit tristique sit amet. Donec at facilisis tortor. Integer porta ornare nunc, ut pretium diam tristique vel. Nullam aliquet turpis at ante sodales, elementum lobortis tortor mattis. Sed venenatis sem eu congue consequat. Vestibulum vulputate eleifend mauris, eget pellentesque ligula feugiat ac.

Nunc aliquam erat eget magna venenatis, sed elementum velit efficitur. Mauris non facilisis tortor. Aliquam semper vehicula justo, sed scelerisque quam pellentesque sed. Donec pulvinar accumsan nibh. Suspendisse fermentum dolor mauris, vitae semper arcu efficitur in. Nullam accumsan, nisi a interdum eleifend, neque libero varius velit, eu convallis elit lacus non eros. Proin volutpat est blandit lectus fermentum, eget porttitor sapien fringilla.

Donec molestie porta ligula, vel gravida erat suscipit vitae. Proin convallis venenatis metus nec aliquet. Curabitur sed mauris ultrices, vehicula neque in, euismod massa. Curabitur non lacus viverra, lacinia turpis sit amet, aliquet turpis. Duis nec risus risus. Vestibulum dignissim magna sit amet porta ultricies. Sed volutpat at urna vitae aliquet. Vestibulum tristique risus orci, molestie condimentum velit euismod vel. Phasellus consectetur vitae purus vel imperdiet. Donec ac porta tortor. Aenean faucibus ligula non imperdiet fringilla. Praesent ut faucibus tellus. Donec lacinia ligula ultricies enim vulputate hendrerit.


