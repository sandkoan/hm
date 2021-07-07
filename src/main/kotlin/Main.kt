fun main() {

    val var1 = newVariable()
    val var2 = newVariable()
    val pairtype = Oper("×", arrayListOf(var1, var2))

    val var3 = newVariable()

    val myenv = mutableMapOf(
        "pair" to Function(var1, Function(var2, pairtype)),
        "true" to Bool,
        "cond" to Function(Bool, Function(var3, Function(var3, var3))),
        "zero" to Function(Integer, Bool),
        "pred" to Function(Integer, Integer),
        "times" to Function(Integer, Function(Integer, Integer))
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

fun tryexp(env: Env, ast: SyntaxNode) {
    print("${SyntaxNode.string(ast)} : ")
    try {
        val t = analyse(ast, env)
        print(string(t))
    } catch (e: Exception) {
        print(e.message + " : " + e.stackTrace)
    }
    println()
}
