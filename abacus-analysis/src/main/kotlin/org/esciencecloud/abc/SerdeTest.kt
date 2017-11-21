package org.esciencecloud.abc

import org.esciencecloud.kafka.JsonSerde.jsonSerde

fun main(args: Array<String>) {
    val json = """
        {
            "in_file": {
                "source": "storage://tempZone/home/rods/infile",
                "destination": "files/afile.txt"
            },
            "in_file2": {
                "source": "storage://tempZone/home/rods/infile2",
                "destination": "files/another_file.txt"
            },
            "outfile": {
                "source": "files/another_file.txt",
                "destination": "storage://tempZone/home/rods/outputfile"
            },
            "command": "test",
            "nodes": 42
        }
        """.trimIndent()

    val serde = jsonSerde<Map<String, Any>>()
    val foo = serde.deserializer().deserialize("foo", json.toByteArray())
    println(foo)
}