# zuul

A lock manager service that provides shared and exclusive, shallow and deep locks via a RESTful interface. Next steps are
* Providing a client library that wraps locking operations in AutoCloseable Lock objects and hides the service.
* Use proper intention locks so that invariants about the lock tree even hold during lock operations (and not only between). This will allow to enable assertions and gain more trust in the correctness of the implemntation.
* Replication of locks and reaching consensus among a number of nodes (probably 5) about lock operations using Paxos.
* Compression of lock IDs by efficiently encoding commonly used lock ID prefixes. This must be done on a regular basis and nodes must again reach consensus about the encoding.
* Binary encoding of communication between nodes and between client library and nodes for performance.
