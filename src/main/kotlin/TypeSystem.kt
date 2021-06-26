typealias Env = Map<String, Type>

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
    _nextVariableId+=1
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

fun analyse(ast: SyntaxNode, env: Env): Type = analyse(ast, env, mutableSetOf())
fun analyse(ast: SyntaxNode, env: Env, nongen: Set<Variable>): Type = when (ast) {
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

fun gettype(name: String, env: Env, nongen: Set<Variable>): Type = when {
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
