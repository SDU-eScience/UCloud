:orphan:

# Accounting (Compute)

__Credits__ is the currency used for all payment of compute resources in UCloud. All prices in the UCloud system are
in an _integer_ amount of credits. Credits are always integer as this allow for more precise calculations. All
applications run on a specific machine template, these machine template have an associated price per hour. The system 
will _reserve_ credits from a wallet at the start of every job. We _charge_ the credits from the wallet at the end of
the job.

[__Wallets__](wiki/wallets.html) contain an integer amount of credits and can pay for any jobs running a specific
machine type. A wallet belongs to an entity, this is either a project, or a user (Personal project).

The __balance__ of a wallet describe the contents of a wallet (in credits). The system will remove credits from the 
balance when it is charged for X credits. A wallet can have 0 or more __reservations__ against it. A reservation will 
only succeed if the sum of all reservations is less than the balance.

## Granting Credits to Projects and Overbooking

Project administrators can _grant_ credits to any of its sub-projects. Overbooking is a technique used to improve 
utilization of the system. Concretely, it means that a project can give out more resources than it owns. 

Credits that have been granted to a sub-project do not count against the project's balance. Only the credits which have
been charged count against the project's balance. As a result, reservations and charges will apply recursively to their
parent's wallets.

A reservation action against a wallet performs limit checking, as described above. The check listed above, is not only
run for the local project but also for all the parent projects. If any parent project has run out of credits then
non of the descending projects can use any more credits.

### Example

S1:

```text
SDU/ (balance: 100c, reservation: 0c)
  NAT/ (balance: 80c, reservation: 0c)
    IMADA/ (balance: 80c, reservation: 0c)
      Project (balance: 50c, reservation: 0c)
    BMB/ (balance: 80c, reservation: 0c)
      Project (balance: 80c, reservation: 0c)
  HUM/ (balance: 40c, reservation: 0c)
    ...
```

Action: `/SDU/NAT/IMADA/Project` reserves 10c for a run

S2:

```text
SDU/ (balance: 100c, reservation: 10c)
  NAT/ (balance: 80c, reservation: 10c)
    IMADA/ (balance: 80c, reservation: 10c)
      Project (balance: 50c, reservation: 10c)
    BMB/ (balance: 80c, reservation: 0c)
      Project (balance: 80c, reservation: 0c)
  HUM/ (balance: 40c, reservation: 0c)
    ...
```

Action: `/SDU/NAT/IMADA/Project` charges 5c from the run

S3:

```text
SDU/ (balance: 95c, reservation: 0c)
  NAT/ (balance: 75c, reservation: 0c)
    IMADA/ (balance: 75c, reservation: 0c)
      Project (balance: 45c, reservation: 0c)
    BMB/ (balance: 80c, reservation: 0c)
      Project (balance: 80c, reservation: 0c)
  HUM/ (balance: 40c, reservation: 0c)
    ...
```

Action: `/SDU/NAT/IMADA/Project` reserves 45c for a run

S4:

```text
SDU/ (balance: 95c, reservation: 45c)
  NAT/ (balance: 75c, reservation: 45c)
    IMADA/ (balance: 75c, reservation: 45c)
      Project (balance: 45c, reservation: 45c)
    BMB/ (balance: 80c, reservation: 0c)
      Project (balance: 80c, reservation: 0c)
  HUM/ (balance: 40c, reservation: 0c)
    ...
```

Action: `/SDU/NAT/BMB/Project` reserves 50c for a run

- The local wallet has enough credits. The check now goes to the parent.
- `/SDU/NAT/BMB` has enough credits. The check now goes to the parent.
- `/SDU/NAT`'s reservation would exceed its own balance. Reject the request.

Action: `/SDU/NAT/IMADA/Project` charges 0c for a run

Action: `/SDU/NAT/IMADA/Project/Sub` is created

S5:

```text
SDU/ (balance: 95c, reservation: 0c)
  NAT/ (balance: 75c, reservation: 0c)
    IMADA/ (balance: 75c, reservation: 0c)
      Project/ (balance: 45c, reservation: 0c)
        Sub/ (balance: 0c, reservation: 0c)
    BMB/ (balance: 80c, reservation: 0c)
      Project (balance: 80c, reservation: 0c)
  HUM/ (balance: 40c, reservation: 0c)
    ...
```

Action: `/SDU/NAT/IMADA/Project/Sub` is granted 1000c

S6:

```text
SDU/ (balance: 95c, reservation: 0c)
  NAT/ (balance: 75c, reservation: 0c)
    IMADA/ (balance: 75c, reservation: 0c)
      Project/ (balance: 45c, reservation: 0c)
        Sub/ (balance: 1000c, reservation: 0c)
    BMB/ (balance: 80c, reservation: 0c)
      Project (balance: 80c, reservation: 0c)
  HUM/ (balance: 40c, reservation: 0c)
    ...
```

Note: `/SDU/NAT/IMADA/Project/Sub` receives a warning because less than 75% of their balance is actually usable.
This is checked by comparing the project's balance with its parent's balance. This warning is also visible for admins of
`/SDU/NAT/IMADA/Project`.

Action: `/SDU/NAT/IMADA/Project/Sub` reserves 1000c

- The local wallet has enough credits. The check now goes to the parent.
- `/SDU/NAT/IMADA/Project`'s reservation would exceed its own balance. Reject the request.
