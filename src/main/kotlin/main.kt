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

class TypeError(msg: String) : Exception(msg)

class ParseError(msg: String) : Exception(msg)

object TypeSystem {
    sealed class Type
    data class Variable(val id: Int) : Type() {
        var instance: Type? = null
        val name : String by lazy { nextUniqueName() }
    }

    data class Oper(val name: String, val args: List<Type>) : Type()

    fun Function(from: Type, to: Type) = Oper("->", listOf(from, to))
    val Integer = Oper("int", arrayListOf())
    val Bool = Oper("bool", arrayListOf())

    var _nextVariableName = 'α'

    fun nextUniqueName(): String {
        val result = _nextVariableName
        _nextVariableName = (_nextVariableName.code + 1).toChar()
        return result.toString()
    }

    var _nextVariableId = 0

    fun newVariable(): Variable {
        val result = _nextVariableId
        _nextVariableId++
        return Variable(result)
    }

    fun string(t: Type): String = when (t) {
        is Variable -> if (t.instance != null) string(t.instance!!) else t.name
        is Oper -> {
            val (name, args) = t
            when {
                args.isEmpty() -> name
                args.size == 2 -> "(${string(args[0])} $name ${string(args[1])})"
                else -> args.joinToString(" ", "$name ")
            }
        }
    }

    fun analyse(ast: SyntaxNode, env: Map<String, Type>): Type = analyse(ast, env, mutableSetOf())
    fun analyse(ast: SyntaxNode, env: Map<String, Type>, nongen: Set<Variable>): Type = when (ast) {
        is Ident -> gettype(ast.name, env, nongen)
        is Apply -> {
            val (fn, arg) = ast
            val funtype = analyse(fn, env, nongen)
            val argtype = analyse(arg, env, nongen)
            val resulttype = newVariable()
            unify(Function(argtype, resulttype), funtype)
            resulttype
        }
        is Lambda -> {
            val (arg, body) = ast
            val argtype = newVariable()
            val resulttype = analyse(body,
                env + (arg to argtype),
                nongen + argtype)
            Function(argtype, resulttype)
        }
        is Let -> {
            val (v, defn, body) = ast
            val defntype = analyse(defn, env, nongen)
            val newenv = env + (v to defntype)
            analyse(body, newenv, nongen)
        }
        is Letrec -> {
            val (v, defn, body) = ast
            val newtype = newVariable()
            val newenv = env + (v to newtype)
            val defntype = analyse(defn, newenv, nongen + newtype)
            unify(newtype, defntype)
            analyse(body, newenv, nongen)
        }
    }

    fun gettype(name: String, env: Map<String, Type>, nongen: Set<Variable>): Type = when {
        env.contains(name) -> fresh(env[name]!!, nongen)
        isIntegerLiteral(name) -> Integer
        else -> throw ParseError("Undefined symbol $name")
    }

    fun fresh(t: Type, nongen: Set<Variable>): Type {
        val mappings = mutableMapOf<Variable, Variable>()
        fun freshrec(tp: Type): Type {
            return when (val v = prune(tp)) {
                is Variable -> {
                    if (isgeneric(v, nongen)) mappings.getOrPut(v) { newVariable() }
                    else v
                }
                is Oper -> Oper(v.name, v.args.map { freshrec(it) })
            }
        }
        return freshrec(t)
    }

    fun unify(t1: Type, t2: Type) {
        val type1 = prune(t1)
        val type2 = prune(t2)
        if (type1 is Variable) {
            if (type1 != type2) {
                if (occursintype(type1, type2))
                    throw TypeError("recursive unification")
                type1.instance = type2
            }
        } else if (type1 is Oper && type2 is Variable) {
            unify(type1, type2)
        } else if (type1 is Oper && type2 is Oper) {
            if (type1.name != type2.name ||
                type1.args.size != type2.args.size
            ) throw TypeError("Type mismatch: ${string(type1)} != ${string(type2)}")
            for (i in 0 until type1.args.size)
                unify(type1.args[i], type2.args[i])
        }
    }

    fun prune(t: Type): Type = when (t) {
        is Variable -> {
            if (t.instance != null) {
                val inst = prune(t.instance!!)
                t.instance = inst
                inst
            } else {
                t
            }
        }
        else -> t
    }

    fun isgeneric(v: Variable, nongen: Set<Variable>) = !(occursin(v, nongen))

    fun occursintype(v: Variable, type2: Type): Boolean = when (val p = prune(type2)) {
        v -> true
        is Oper -> occursin(v, p.args)
        else -> false
    }

    fun occursin(t: Variable, list: Iterable<Type>): Boolean = list.any { t2 -> occursintype(t, t2) }

    val checkDigits = Regex("^(\\d+)$")
    fun isIntegerLiteral(name: String) = checkDigits.find(name) != null
}

object HindleyMilner {
    fun main() {

        val var1 = TypeSystem.newVariable()
        val var2 = TypeSystem.newVariable()
        val pairtype = TypeSystem.Oper("×", arrayListOf(var1, var2))

        val var3 = TypeSystem.newVariable()

        val myenv = mutableMapOf(
            "pair" to TypeSystem.Function(var1, TypeSystem.Function(var2, pairtype)),
            "true" to TypeSystem.Bool,
            "cond" to TypeSystem.Function(TypeSystem.Bool, TypeSystem.Function(var3, TypeSystem.Function(var3, var3))),
            "zero" to TypeSystem.Function(TypeSystem.Integer, TypeSystem.Bool),
            "pred" to TypeSystem.Function(TypeSystem.Integer, TypeSystem.Integer),
            "times" to TypeSystem.Function(TypeSystem.Integer,
                TypeSystem.Function(TypeSystem.Integer, TypeSystem.Integer))
        )

        val pair = Apply(Apply(Ident("pair"), Apply(Ident("f"), Ident("4"))), Apply(Ident("f"), Ident("true")))
        val examples = arrayListOf(
            // factorial
            Letrec("factorial", // letrec factorial =
                Lambda("n",    // fn n =>
                    Apply(
                        Apply(   // cond (zero n) 1
                            Apply(Ident("cond"),     // cond (zero n)
                                Apply(Ident("zero"), Ident("n"))),
                            Ident("1")),
                        Apply(    // times n
                            Apply(Ident("times"), Ident("n")),
                            Apply(Ident("factorial"),
                                Apply(Ident("pred"), Ident("n")))
                        )
                    )
                ),      // in
                Apply(Ident("factorial"), Ident("5"))
            ),

            // Should fail:
            // fn x => (pair(x(3) (x(true)))
            Lambda("x",
                Apply(
                    Apply(Ident("pair"),
                        Apply(Ident("x"), Ident("3"))),
                    Apply(Ident("x"), Ident("true")))),

            // pair(f(3), f(true))
            Apply(
                Apply(Ident("pair"), Apply(Ident("f"), Ident("4"))),
                Apply(Ident("f"), Ident("true"))),


            // letrec f = (fn x => x) in ((pair (f 4)) (f true))
            Let("f", Lambda("x", Ident("x")), pair),

            // fn f => f f (fail)
            Lambda("f", Apply(Ident("f"), Ident("f"))),

            // let g = fn f => 5 in g g
            Let("g",
                Lambda("f", Ident("5")),
                Apply(Ident("g"), Ident("g"))),

            // example that demonstrates generic and non-generic variables:
            // fn g => let f = fn x => g in pair (f 3, f true)
            Lambda("g",
                Let("f",
                    Lambda("x", Ident("g")),
                    Apply(
                        Apply(Ident("pair"),
                            Apply(Ident("f"), Ident("3"))
                        ),
                        Apply(Ident("f"), Ident("true"))))),

            // Function composition
            // fn f (fn g (fn arg (f g arg)))
            Lambda("f", Lambda("g", Lambda("arg", Apply(Ident("g"), Apply(Ident("f"), Ident("arg"))))))
        )
        for (example in examples) {
            tryexp(myenv, example)
        }
    }

    fun tryexp(env: Map<String, TypeSystem.Type>, ast: SyntaxNode) {
        print("${SyntaxNode.string(ast)} : ")
        try {
            val t = TypeSystem.analyse(ast, env)
            print(TypeSystem.string(t))
        } catch (e: Exception) {
            print(e.message + " : " + e.stackTrace)
        }
        println()
    }
}

fun main() {
    HindleyMilner.main()
}
