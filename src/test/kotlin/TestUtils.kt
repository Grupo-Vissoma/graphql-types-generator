import java.io.ByteArrayOutputStream
import java.io.PrintStream

fun captureLogs(block: () -> Unit): List<String> {
    val output = mutableListOf<String>()
    val originalOut = System.out
    val originalErr = System.err

    try {
        System.setOut(PrintStream(ByteArrayOutputStream().also { outputStream ->
            System.setOut(PrintStream(outputStream))
        }))
        System.setErr(System.out)

        block()
    } finally {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    return output.map { it.trim() }.filter { it.isNotEmpty() }
}