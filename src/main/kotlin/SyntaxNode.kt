sealed class SyntaxNode {
    companion object {
        fun string(ast: SyntaxNode): String =
            if (ast is Ident) nakedString(ast) else "(${nakedString(ast)})"

        fun nakedString(ast: SyntaxNode): String = when (ast) {
            is Ident -> ast.name
            is Lambda -> "fn ${ast.v} => ${string(ast.body)}"
            is Apply -> "${string(ast.fn)} ${string(ast.arg)}"
            is Let -> "let ${ast.v} = ${string(ast.defn)} in ${string(ast.body)}"
            is Letrec -> "letrec ${ast.v} = ${string(ast.defn)} in ${string(ast.body)}"
        }
    }
}

data class Lambda(val v: String, val body: SyntaxNode) : SyntaxNode()

data class Ident(val name: String) : SyntaxNode()

data class Apply(val fn: SyntaxNode, val arg: SyntaxNode) : SyntaxNode()

data class Let(val v: String, val defn: SyntaxNode, val body: SyntaxNode) : SyntaxNode()

data class Letrec(val v: String, val defn: SyntaxNode, val body: SyntaxNode) : SyntaxNode()
