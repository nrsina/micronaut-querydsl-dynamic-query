# DynQuery 
## A Dynamic REST Query Language in Micronaut

### Introduction

Sometimes in web-based applications, you might end up needing an **"advanced search"** feature for your entities which happens to have many fields and relations. Supporting each of these search requirements results in a huge amount of code and a tight coupling between the backend code and UI. And if some business requirement changes, you have to update your backend code to support the newly requested features. But a dynamic query builder that can convert REST query parameters to SQL queries can remove this tight coupling and make developers' lives easier!

Spring framework has built-in support for these kinds of dynamic queries in its Spring Data module by leveraging the *QueryDSL* library and it also has [web support](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#core.web.type-safe) so it can dynamically create SQL queries from the REST parameters.

By taking ideas from Spring Data, I tried to implement a dynamic REST query language in Micronaut by using JPA and QueryDSL with no runtime reflections thanks to Micronaut's awesome compile-time Introspection which can result in less memory usage and better performance which is suitable for Microservices architecture.

Please visit [snourian.com](https://snourian.com) for more details:

[Implementing a Dynamic REST Query Language in Micronaut with JPA and QueryDSL](https://snourian.com/dynamic-rest-query-language-micronaut-jpa-querydsl)

### Recent releases
* **0.2.0** - first preview release

### Requirements
* Micronaut Framework
* Micronaut Data
* JPA

### Installation
Add this library as a dependency to your Micronaut project:
```groovy
implementation "com.snourian.micronaut:querydsl-dynamic-query:0.2.0"
```
### How to use
1. Change your Repository from interface to an abstract class and implement **QuerydslPredicateExecutor\<Entity>**. You have to implement two methods from the interface
```java
@Repository
public abstract class DepartmentRepository implements JpaRepository<Department, Long>,
        QuerydslPredicateExecutor<Department> {

    @PersistenceContext
    private EntityManager em;

    @Override
    public EntityManager getEntityManager() {
        return em;
    }

    @Override
    public Class<Department> getEntityClass() {
        return Department.class;
    }
}
```
2. Add an endpoint to your controller class like below and call the methods from *QuerydslPredicateExecutor* interface :
```java
@Get("/search{?values*}")
public HttpResponse<?> search(@RequestBean QueryParameters values, Pageable pageable) {
    Page<Department> result = departmentRepository.findAll(values, pageable);
    return HttpResponse.ok(result.map(mapper::toDto));
}
```
3. Now call this endpoint and write your predicates as HTTP params:

```text
GET /search
Query: select department from Department department

GET /search?id=eq(11)
Query: select department from Department department where department.id = ?1

GET /search?location.city=eq(Gotham)
Query: select department from Department department where department.location.city = ?1

GET /search?employees.rank=in(Manager,Chief)&employees.gender=eq(Female)
Query: select department from Department department
  inner join department.employees as department_employees
where department_employees.rank in (?1, ?2) and department_employees.gender = ?3

GET /search?projects.title=string_contains_ic(stream)&projects.technologies.name=in(Python,Go)
Query: select department from Department department
  inner join department.projects as department_projects
  inner join department_projects.technologies as department_projects_technologies
where lower(department_projects.title) like ?1 escape '!' and department_projects_technologies.name in (?2, ?3)

GET /search?employees.score=gt(70)&employees.rank=ne(Manager)&sort=id,desc&page=2&size=1
Query: select department from Department department
  inner join department.employees as department_employees
where department_employees.score > ?1 and department_employees.rank <> ?2
order by department.id desc limit ? offset ?
```

If you want to make a "OR" predicate, just add **'EXPR_TYPE=anyOf'** to your parameters:

```http request
GET /search?employees.score=gt(70)&employees.rank=eq(Manager)&EXPR_TYPE=anyOf
Query: select department from Department department
  inner join department.employees as department_employees
where department_employees.score > ?1 or department_employees.rank = ?2
```

To see what types of predicates are supported, check the **com.snourian.micronaut.querydsl.expression.operator.PredicateOperator** enum:
```text
EQ, NE, IS_NULL, IS_NOT_NULL, BETWEEN, GOE, GT, LOE, LT, 
MATCHES (regex), MATCHES_IC (regex), STRING_IS_EMPTY, STARTS_WITH, 
STARTS_WITH_IC, EQ_IGNORE_CASE, ENDS_WITH, ENDS_WITH_IC, STRING_CONTAINS, 
STRING_CONTAINS_IC, LIKE, LIKE_IC, LIKE_ESCAPE, LIKE_ESCAPE_IC, IN, NOT_IN
```

If you want to add some general customizations to the final *Predicate* object, you can implement **customize()** method inside the repository class
```java
@Repository
public abstract class DepartmentRepository implements JpaRepository<Department, Long>,
        QuerydslPredicateExecutor<Department> {

    @Override
    public Predicate customize(Predicate predicate) {
        // Make some customizations to the final predicate. For instance, append an AND or OR predicate
        // Refer to QueryDSL documentation to see how to work with Predicates and Expressions
        return predicate;
    }
}
```

### What's not working
+ Fields with @ElementCollection annotation
+ Map\<?,?> relations

## Contribution
This is the very first version of the library and unfortunately, I have very limited free time so I might not be able to update it regularly. Therefore, contributions are very welcome! Help me make this library better :blush:

Some refactoring and optimization ideas:
+ Use a tree data structure for **PredicateEntry** class instead of the current implementation and traverse it with DFS when building the final query.
+ Refactor **ExpressionFactory** class to use less *if/else* statements.
+ Only call **BeanIntrospection** once for the root Entity in **PredicateEntry** class.
+ Currently, this library only supports "AND" and "OR" expressions. Support complex expressions by combining and/or predicates instead of only using "AND" or "OR".
+ I tried my best not to use reflection and this library heavily relies on Micronaut's compile-time bean introspection. But I haven't tested compiling to native image. It might need some work to support it; or maybe not!
+ Adding debug/trace logs and unit tests!