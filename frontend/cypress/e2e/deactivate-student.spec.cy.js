// UC-STU-deactivate-student: the Deactivate control on the Students page. The API calls are stubbed, so these tests
// check what the page offers and sends; the backend's StudentDeactivationIntegrationTest covers who may deactivate,
// the 409, and the peer evaluation consequences. Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const student = (id, firstName, lastName, enabled) => ({
  id,
  username: `${firstName[0].toLowerCase()}.${lastName.toLowerCase()}@abc.edu`,
  firstName,
  lastName,
  email: `${firstName[0].toLowerCase()}.${lastName.toLowerCase()}@abc.edu`,
  teamName: 'Team2',
  enabled
})

const page = (content) => ({
  flag: true,
  code: 200,
  message: 'Find students successfully',
  data: { content, number: 0, size: 50, totalElements: content.length }
})

describe('Deactivate a student', () => {
  beforeEach(() => {
    cy.login('b.gates@abc.edu', '123456')
    cy.intercept('POST', '**/students/search*', {
      body: page([student(7, 'Woody', 'Allen', true), student(8, 'Amanda', 'Wagner', false)])
    }).as('search')
    cy.intercept('PATCH', '**/students/*/deactivate', {
      body: {
        flag: true,
        code: 200,
        message: 'Deactivate student successfully',
        data: student(7, 'Woody', 'Allen', false)
      }
    }).as('deactivate')
    cy.visit('/students')
    cy.wait('@search')
  })

  it('offers Deactivate only for an active student (3a1)', () => {
    cy.contains('tr', 'Woody').find('[data-cy=deactivate-student]').should('exist')
    cy.contains('tr', 'Amanda').find('[data-cy=deactivate-student]').should('not.exist')
  })

  it('sends nothing when the confirmation is cancelled (5a)', () => {
    cy.contains('tr', 'Woody').find('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box')
      .should('contain', 'Deactivate a student')
      .and('contain', 'can be reactivated')
    cy.get('.el-message-box').contains('button', 'Cancel').click()
    cy.get('.el-message-box').should('not.exist')
    cy.get('@deactivate.all').should('have.length', 0)
  })

  it('deactivates on confirmation and reloads the list (steps 4-6)', () => {
    cy.contains('tr', 'Woody').find('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box').contains('button', 'Deactivate').click()
    cy.wait('@deactivate')
      .its('request.url')
      .should('match', /\/students\/7\/deactivate$/)
    cy.contains('The student has been deactivated.')
    cy.wait('@search') // the list reloads
  })

  it('shows the server message once and reloads the list when she is already deactivated (3a2)', () => {
    // Another user deactivated her after this page loaded: the PATCH is refused, and the reload shows her deactivated
    cy.intercept('PATCH', '**/students/*/deactivate', {
      statusCode: 409,
      body: { flag: false, code: 409, message: 'The student is already deactivated.', data: null }
    }).as('conflict')
    cy.intercept('POST', '**/students/search*', {
      body: page([student(7, 'Woody', 'Allen', false), student(8, 'Amanda', 'Wagner', false)])
    }).as('reload')

    cy.contains('tr', 'Woody').find('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box').contains('button', 'Deactivate').click()
    cy.wait('@conflict')
    cy.wait('@reload') // the list reloads, which refreshes her details

    // The server's message is the only toast: the page adds neither a second error nor the success message
    cy.get('.el-message')
      .should('have.length', 1)
      .and('have.class', 'el-message--error')
      .and('contain', 'The student is already deactivated.')
    cy.contains('The student has been deactivated.').should('not.exist')
    cy.contains('tr', 'Woody').find('[data-cy=deactivate-student]').should('not.exist')
  })
})
